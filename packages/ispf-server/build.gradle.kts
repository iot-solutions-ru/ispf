plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-reload4j")
    exclude(group = "ch.qos.reload4j", module = "reload4j")
}

// ispf-ai-agent depends on this project; include its outputs via soft classpath
// (not project()) to avoid a Gradle circular dependency.
val aiAgentProject = project(":packages:ispf-ai-agent")
val aiAgentMainOutput = aiAgentProject.layout.buildDirectory.dir("classes/java/main")
val aiAgentMainResources = aiAgentProject.layout.buildDirectory.dir("resources/main")
val aiAgentJar = aiAgentProject.tasks.named("jar")

dependencies {
    implementation(project(":packages:ispf-core"))
    implementation(project(":packages:ispf-expression"))
    implementation(project(":packages:ispf-analytics-engine"))
    implementation(project(":packages:ispf-analytics-api"))
    implementation(project(":packages:ispf-analytics-core-ext"))
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-plugin-blueprint"))
    implementation(project(":packages:ispf-plugin-workflow"))
    implementation(project(":packages:ispf-ai-api"))
    implementation(project(":packages:ispf-ai-openai-compatible"))
    implementation(project(":packages:ispf-ai-ollama"))

    implementation("io.nats:jnats:2.26.0")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-registry-otlp")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("org.apache.cassandra:java-driver-core:4.19.3")

    implementation("com.haulmont.yarg:yarg:2.2.22") {
        exclude(group = "javax.xml.bind", module = "jaxb-api")
    }
    // YARG XlsxFormatter uses docx4j; JDK 11+ removed internal JAXB — required for .xlsx templates
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.9")
    implementation("org.docx4j:docx4j-JAXB-ReferenceImpl:17.0.0")

    implementation("org.apache.parquet:parquet-avro:1.14.3") {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
    }
    implementation("org.apache.parquet:parquet-hadoop:1.14.3") {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
    }
    implementation("org.apache.avro:avro:1.11.4")
    implementation("org.apache.hadoop:hadoop-common:3.4.1") {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
    }

    constraints {
        implementation("net.sf.jasperreports:jasperreports:7.0.7")
        implementation("net.sf.jasperreports:jasperreports-fonts:7.0.7")
        implementation("commons-beanutils:commons-beanutils:1.11.0")
        implementation("com.lowagie:itext:4.2.2")
    }

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("com.h2database:h2")
}

val webConsoleDistDir = rootProject.layout.projectDirectory.dir("apps/web-console/dist")

fun shouldEmbedWebConsole(): Boolean {
    // Opt-in only: auto-embedding a local apps/web-console/dist would pollute test
    // classpath and every bootJar. Release workflow passes -PembedWebConsole=true.
    if (findProperty("embedWebConsole")?.toString() != "true") {
        return false
    }
    val indexHtml = webConsoleDistDir.file("index.html").asFile
    if (!indexHtml.exists()) {
        throw GradleException(
            "embedWebConsole=true but apps/web-console/dist/index.html is missing. " +
                "Build the console first: cd apps/web-console && npm ci && npm run build"
        )
    }
    return true
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("ispf-server")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(aiAgentJar)
    classpath(aiAgentJar)
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
    doFirst {
        // Fail early on release builds that require an all-in-one JAR.
        shouldEmbedWebConsole()
    }
}

springBoot {
    buildInfo {
        excludes.set(setOf("time"))
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(rootProject.tasks.named("buildContextPack"))
    from(rootProject.file("gradle/driver-packs.json")) {
        into("driver-pack")
    }
    // All-in-one JAR: serve the built web console from classpath:/static when dist exists
    // (release workflow builds the console first; local bootJar skips when dist is absent).
    if (shouldEmbedWebConsole()) {
        from(webConsoleDistDir) {
            into("static")
        }
        logger.lifecycle("Embedding web-console from {}", webConsoleDistDir.asFile)
    }
}

val driverPacksDir = rootProject.layout.buildDirectory.dir("driver-packs")

fun driverPackEnsureTaskName(): String =
    if (System.getProperty("ispf.driver.packs") == "all") "ensureAllDriverPacks" else "ensureDevDriverPacks"

tasks.named<Test>("test") {
    dependsOn(tasks.named("bootBuildInfo"))
    dependsOn(rootProject.tasks.named(driverPackEnsureTaskName()))
    dependsOn(aiAgentProject.tasks.named("classes"))
    classpath += files(aiAgentMainOutput, aiAgentMainResources)
    val packsPath = driverPacksDir.get().asFile.absolutePath
    environment("ISPF_DRIVER_PACKS_DIR", packsPath)
    systemProperty("ISPF_DRIVER_PACKS_DIR", packsPath)
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    maxHeapSize = "10g"
    // Many @SpringBootTest classes in one JVM can pollute shared async shutdown (CI flakes).
    if (System.getenv("CI") != null) {
        forkEvery = 1
        systemProperty("spring.test.context.cache.maxSize", "1")
    }
    useJUnitPlatform {
        if (System.getProperty("ispf.test.skipLoad") == "true") {
            excludeTags("load")
        }
        if (System.getProperty("ispf.test.skipFederation") == "true") {
            excludeTags("federation")
        }
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(rootProject.tasks.named(driverPackEnsureTaskName()))
    dependsOn(aiAgentProject.tasks.named("classes"))
    classpath += files(aiAgentMainOutput, aiAgentMainResources)
    environment("ISPF_DRIVER_PACKS_DIR", driverPacksDir.get().asFile.absolutePath)
}
