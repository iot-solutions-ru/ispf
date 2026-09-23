import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("net.ltgt.errorprone") version "5.1.1" apply false
    // SBOM for the nightly dependency vulnerability scan (Trivy): ./gradlew cyclonedxBom
    id("org.cyclonedx.bom") version "3.4.1"
}

// Aggregate CycloneDX SBOM over every module's runtimeClasspath (server + all driver packs + AI
// providers): build/reports/cyclonedx/bom.json. Nightly CI scans it with Trivy (see nightly.yml).
// Only shipped classpaths: test-only libraries (embedded brokers, fixtures) are not attack surface.
tasks.cyclonedxBom {
    componentName.set("ispf-platform")
}
allprojects {
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        includeConfigs.set(listOf("runtimeClasspath"))
    }
}

allprojects {
    group = "com.ispf"
    version = findProperty("version")?.toString() ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

/**
 * JaCoCo coverage floors per module (code-analysis F-02): LINE and BRANCH covered ratio.
 *
 * Ratchet policy: the floors sit ~3 pp (line) / ~4 pp (branch) under the measured CI baseline of
 * 2026-09-20 (pr-fast slice: `-Dispf.test.skipLoad=true -Dispf.test.skipFederation=true`).
 * Raise a floor when a module's coverage grows; never lower one silently вЂ” a drop means tests
 * were deleted or untested code landed, and that is exactly what the gate is for.
 * Modules absent from this map only get a report (`coverageReport`), no verification.
 */
val coverageFloors: Map<String, Pair<Double, Double>> = mapOf(
    //  module                    line  branch   (baseline: line / branch)
    "ispf-core" to (0.31 to 0.13),             // 34.5 / 17.3
    "ispf-expression" to (0.61 to 0.46),       // 64.7 / 50.5
    "ispf-plugin-blueprint" to (0.45 to 0.28), // 48.0 / 32.4
    "ispf-plugin-workflow" to (0.72 to 0.52),  // 75.6 / 56.3
    "ispf-server" to (0.61 to 0.43),           // 64.7 / 47.3
    "ispf-ai-agent" to (0.56 to 0.35),         // 59.3 / 39.8
)

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    if (name.startsWith("ispf-driver-")
        && name != "ispf-driver-api"
        && name != "ispf-driver-ddk"
        && name != "ispf-driver-stub-kit"
    ) {
        apply(plugin = "ispf-driver-pack")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    // Static analysis (code-analysis F-02): Error Prone runs inside javac on every compile.
    // ERROR-severity bug patterns fail the build; WARNING patterns are reported. Local opt-out
    // for a quick iteration: -Pispf.errorprone=false (CI always compiles with it on).
    if (findProperty("ispf.errorprone")?.toString() != "false") {
        apply(plugin = "net.ltgt.errorprone")
        dependencies {
            "errorprone"("com.google.errorprone:error_prone_core:2.50.0")
        }
        tasks.withType<JavaCompile> {
            options.errorprone {
                disableWarningsInGeneratedCode.set(true)
                // Promoted from WARNING: each of these is a real defect class that was found and fixed in the
                // 2026-09 sweep (wire bytes depending on the JVM default charset, unclosed directory streams,
                // racy counters, literal "%s" in exception messages, swapped arguments, dead conditions).
                error(
                    "DefaultCharset",
                    "StreamResourceLeak",
                    "NonAtomicVolatileUpdate",
                    "OrphanedFormatString",
                    "ArgumentSelectionDefectChecker",
                    "AlreadyChecked",
                    "DuplicateBranches",
                    "MissingOverride",
                )
                // Repo-wide opt-outs — style checks that are not bug patterns; keep each with a reason.
                disable(
                    "MissingSummary",    // Javadoc summary-fragment style (Google style guide), not a defect
                    "InvalidInlineTag",  // Javadoc `{@code}` vs backticks — docs hygiene, not a defect
                    "EscapedEntity",     // Javadoc HTML entities — same
                    "AddressSelection",  // InetAddress.getByName(host) is the intended API for driver hosts;
                                         // multi-homed selection is the operator's DNS concern
                )
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        maxParallelForks = 1
        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    }

    // Coverage: `./gradlew coverageReport` after tests writes
    // <module>/build/reports/jacoco/test/jacocoTestReport.xml (Codecov/Sonar-style tooling);
    // `./gradlew coverageVerify` fails the build when a module in coverageFloors drops below its floor.
    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.14"
    }
    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }
    coverageFloors[name]?.let { (lineFloor, branchFloor) ->
        tasks.withType<JacocoCoverageVerification> {
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = lineFloor.toBigDecimal()
                    }
                }
                rule {
                    limit {
                        counter = "BRANCH"
                        value = "COVEREDRATIO"
                        minimum = branchFloor.toBigDecimal()
                    }
                }
            }
        }
    }

    dependencies {
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    // Pin (ADR-0059 registry). Keep protobuf runtime ahead of CEL / OTel gencode (runtime must be >= gencode).
    // Without this, CEL 0.14+ can fail with ProtobufRuntimeVersionException when an older
    // transitive protobuf-java (e.g. 4.34.x from Micrometer/OTel) wins resolution.
    // Bouncy Castle is pulled transitively at 1.78.1 (Eclipse Milo) and 1.82 (hadoop-common); both carry
    // CRITICAL CVEs (CVE-2025-14813, CVE-2026-8763, CVE-2026-13506). Crypto must resolve to one patched line.
    // Pin (ADR-0059 registry): drop when Milo/Hadoop declare >= 1.85.
    configurations.configureEach {
        resolutionStrategy {
            force(
                "com.google.protobuf:protobuf-java:4.36.2",
                "com.google.protobuf:protobuf-java-util:4.36.2",
                "com.google.protobuf:protobuf-javalite:4.36.2",
                "org.bouncycastle:bcprov-jdk18on:1.86",
                "org.bouncycastle:bcpkix-jdk18on:1.86",
                "org.bouncycastle:bcutil-jdk18on:1.86",
                // kafka-clients 4.3.1 -> lz4-java 1.10.2 (CVE-2026-59949); hadoop-common 3.5.0 ->
                // commons-configuration2 2.10.1 (CVE-2026-45205). Drop when the parents move.
                "at.yawk.lz4:lz4-java:1.11.3",
                "org.apache.commons:commons-configuration2:2.15.1",
            )
        }
    }
}

// Cross-subproject test serialization removed (issue #65 long-term). Server tests use forkEvery=1
// in CI; @Isolated / @Tag("federation") gate slow suites. Opt-in for local flake hunts:
// -Dispf.test.serializeSubprojects=true
gradle.projectsEvaluated {
    if (System.getProperty("ispf.test.serializeSubprojects") != "true") return@projectsEvaluated
    val testTasks = subprojects
        .flatMap { project -> project.tasks.withType<Test>().toList() }
        .sortedBy { it.path }
    for (index in 1 until testTasks.size) {
        testTasks[index].mustRunAfter(testTasks[index - 1])
    }
}

val driverPackProjects = subprojects.filter {
    it.name.startsWith("ispf-driver-")
        && it.name != "ispf-driver-api"
        && it.name != "ispf-driver-ddk"
        && it.name != "ispf-driver-stub-kit"
}

/** Minimal packs for local bootRun, PR-fast, and most integration tests (issue #65). */
val devDriverPackProjectNames = listOf(
    "ispf-driver-virtual",
    "ispf-driver-mqtt",
    "ispf-driver-kafka",
    "ispf-driver-modbus",
    "ispf-driver-http",
    "ispf-driver-email",
    "ispf-driver-sms",
    "ispf-driver-webhook",
    "ispf-driver-cwmp",
    "ispf-driver-flexible",
    "ispf-driver-gps-tracker",
    "ispf-driver-application",
)

val devDriverPackProjects = driverPackProjects.filter { it.name in devDriverPackProjectNames }

tasks.register("assembleAllDriverPacks") {
    group = "driver packs"
    description = "Assemble all ISPF driver pack directories"
    dependsOn(driverPackProjects.map { it.path + ":assembleDriverPack" })
}

tasks.register("assembleDevDriverPacks") {
    group = "driver packs"
    description = "Assemble dev/minimal driver packs (virtual, mqtt, modbus, http, вЂ¦)"
    dependsOn(devDriverPackProjects.map { it.path + ":assembleDriverPack" })
}

tasks.register<Sync>("syncAllDriverPacks") {
    group = "driver packs"
    description = "Copy all assembled driver packs to build/driver-packs"
    dependsOn("assembleAllDriverPacks")
    into(layout.buildDirectory.dir("driver-packs"))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    driverPackProjects.forEach { project ->
        from(project.layout.buildDirectory.dir("driver-pack")) {
            include("**/*")
        }
    }
}

tasks.register<Sync>("syncDevDriverPacks") {
    group = "driver packs"
    description = "Copy dev/minimal driver packs to build/driver-packs (default for bootRun and tests)"
    dependsOn("assembleDevDriverPacks")
    doFirst {
        if (!driverPacksPrebuilt()) {
            delete(layout.buildDirectory.dir("driver-packs"))
        }
    }
    into(layout.buildDirectory.dir("driver-packs"))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    devDriverPackProjects.forEach { project ->
        from(project.layout.buildDirectory.dir("driver-pack")) {
            include("**/*")
        }
    }
}

fun driverPacksPrebuilt(): Boolean {
    if (System.getenv("ISPF_DRIVER_PACKS_PREBUILT") != "true") return false
    val dir = layout.buildDirectory.dir("driver-packs").get().asFile
    return dir.isDirectory && dir.list()?.isNotEmpty() == true
}

tasks.register("ensureDevDriverPacks") {
    group = "driver packs"
    description = "Sync dev packs unless ISPF_DRIVER_PACKS_PREBUILT=true and build/driver-packs exists (CI cache)"
    onlyIf { !driverPacksPrebuilt() }
    dependsOn("syncDevDriverPacks")
}

tasks.register("ensureAllDriverPacks") {
    group = "driver packs"
    description = "Sync all packs unless ISPF_DRIVER_PACKS_PREBUILT=true and build/driver-packs exists (CI cache)"
    onlyIf { !driverPacksPrebuilt() }
    dependsOn("syncAllDriverPacks")
}

tasks.register("testDevDriverPacks") {
    group = "verification"
    description =
        "Unit-test the minimal/dev driver pack set (PR-fast driver-packs job); full protocol matrix is driver-interop"
    dependsOn(devDriverPackProjects.map { it.path + ":test" })
}

val prFastBackendTestTasks = listOf(
    ":packages:ispf-core:test",
    ":packages:ispf-expression:test",
    ":packages:ispf-export-parquet:test",
    ":packages:ispf-persistence:test",
    ":packages:ispf-server-spi:test",
    ":packages:ispf-concurrent:test",
    ":packages:ispf-plugin-blueprint:test",
    ":packages:ispf-plugin-workflow:test",
    ":packages:ispf-server:test",
    ":packages:ispf-ai-agent:test",
)

tasks.register("testPrFast") {
    group = "verification"
    description = "PR-fast backend slice вЂ” add -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev"
    dependsOn(prFastBackendTestTasks)
}

tasks.register("coverageReport") {
    group = "verification"
    description = "JaCoCo XML+HTML reports for the PR-fast backend modules (run after testPrFast)"
    dependsOn(prFastBackendTestTasks.map { it.removeSuffix(":test") + ":jacocoTestReport" })
}

tasks.register("coverageVerify") {
    group = "verification"
    description = "Fail when a PR-fast module drops below its JaCoCo floor (coverageFloors); run after testPrFast"
    dependsOn(
        prFastBackendTestTasks
            .filter { coverageFloors.containsKey(it.removeSuffix(":test").substringAfterLast(':')) }
            .map { it.removeSuffix(":test") + ":jacocoTestCoverageVerification" },
    )
}

tasks.register("testNightlyBackend") {
    group = "verification"
    description = "Nightly backend module batch вЂ” add -Dispf.test.skipLoad=true -Dispf.driver.packs=dev (federation + load run separately)"
    dependsOn(prFastBackendTestTasks)
}

val contextPackScript = layout.projectDirectory.file("tools/ai-pack/build.py")
val contextPackResource = layout.projectDirectory.file(
    "packages/ispf-ai-agent/src/main/resources/ai/context-pack.json"
)

tasks.register<Exec>("buildContextPack") {
    group = "ai"
    description = "Regenerate ai/context-pack.json from docs and examples (FW-41); runs before server bootJar"
    val python = listOf("python3", "python").firstOrNull { cmd ->
        try {
            providers.exec {
                if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
                    commandLine("cmd", "/c", "where $cmd")
                } else {
                    commandLine("sh", "-c", "command -v $cmd")
                }
                isIgnoreExitValue = true
            }.result.get().exitValue == 0
        } catch (_: Exception) {
            false
        }
    } ?: if (org.gradle.internal.os.OperatingSystem.current().isWindows) "python" else "python3"
    commandLine(python, contextPackScript.asFile.absolutePath)
    environment("ISPF_VERSION", version.toString())
    inputs.file(contextPackScript)
    inputs.dir(layout.projectDirectory.dir("docs/en"))
    inputs.dir(layout.projectDirectory.dir("examples"))
    outputs.file(contextPackResource)
}
