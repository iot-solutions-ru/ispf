plugins {
    java
    id("org.springframework.boot") version "4.0.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.ispf"
    version = findProperty("version")?.toString() ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven {
            name = "ias-releases"
            url = uri("https://maven.mangoautomation.net/repository/ias-release/")
        }
        maven {
            name = "haulmont"
            url = uri("https://repo.cuba-platform.com/content/groups/work")
        }
    }
}

subprojects {
    apply(plugin = "java")

    if (name.startsWith("ispf-driver-") && name != "ispf-driver-api" && name != "ispf-driver-ddk") {
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

    tasks.withType<Test> {
        useJUnitPlatform()
        maxParallelForks = 1
        systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    }

    dependencies {
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    it.name.startsWith("ispf-driver-") && it.name != "ispf-driver-api" && it.name != "ispf-driver-ddk"
}

/** Minimal packs for local bootRun, PR-fast, and most integration tests (issue #65). */
val devDriverPackProjectNames = listOf(
    "ispf-driver-virtual",
    "ispf-driver-mqtt",
    "ispf-driver-modbus",
    "ispf-driver-http",
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
    description = "Assemble dev/minimal driver packs (virtual, mqtt, modbus, http, …)"
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

val prFastBackendTestTasks = listOf(
    ":packages:ispf-core:test",
    ":packages:ispf-expression:test",
    ":packages:ispf-plugin-blueprint:test",
    ":packages:ispf-plugin-workflow:test",
    ":packages:ispf-server:test",
)

tasks.register("testPrFast") {
    group = "verification"
    description = "PR-fast backend slice — add -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev"
    dependsOn(prFastBackendTestTasks)
}

tasks.register("testNightlyBackend") {
    group = "verification"
    description = "Nightly backend module batch — add -Dispf.test.skipLoad=true -Dispf.driver.packs=dev (federation + load run separately)"
    dependsOn(prFastBackendTestTasks)
}

val contextPackScript = layout.projectDirectory.file("tools/ai-pack/build.py")
val contextPackResource = layout.projectDirectory.file(
    "packages/ispf-server/src/main/resources/ai/context-pack.json"
)

tasks.register<Exec>("buildContextPack") {
    group = "ai"
    description = "Regenerate ai/context-pack.json from docs and examples (FW-41); runs before server bootJar"
    commandLine("python", contextPackScript.asFile.absolutePath)
    environment("ISPF_VERSION", version.toString())
    inputs.file(contextPackScript)
    inputs.dir(layout.projectDirectory.dir("docs/en"))
    inputs.dir(layout.projectDirectory.dir("examples"))
    outputs.file(contextPackResource)
}
