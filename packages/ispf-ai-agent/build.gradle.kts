plugins {
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
    }
}

dependencies {
    implementation(project(":packages:ispf-server"))
    implementation(project(":packages:ispf-core"))
    implementation(project(":packages:ispf-expression"))
    implementation(project(":packages:ispf-ai-api"))
    implementation(project(":packages:ispf-ai-openai-compatible"))
    implementation(project(":packages:ispf-ai-ollama"))
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-plugin-blueprint"))
    implementation(project(":packages:ispf-plugin-workflow"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-core")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("com.h2database:h2")
}

val serverProject = project(":packages:ispf-server")

tasks.named<ProcessResources>("processResources") {
    dependsOn(rootProject.tasks.named("buildContextPack"))
}

tasks.named<Test>("test") {
    dependsOn(rootProject.tasks.named("ensureDevDriverPacks"))
    dependsOn(serverProject.tasks.named("processTestResources"))
    // SpringBootTests share server test application-*.yml and fixture bundles.
    classpath += files(serverProject.layout.buildDirectory.dir("resources/test"))
    val packsPath = rootProject.layout.buildDirectory.dir("driver-packs").get().asFile.absolutePath
    environment("ISPF_DRIVER_PACKS_DIR", packsPath)
    systemProperty("ISPF_DRIVER_PACKS_DIR", packsPath)
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    maxHeapSize = "4g"
    useJUnitPlatform()
}
