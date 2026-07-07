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

gradle.projectsEvaluated {
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

tasks.register("assembleAllDriverPacks") {
    group = "driver packs"
    description = "Assemble all ISPF driver pack directories"
    dependsOn(driverPackProjects.map { it.path + ":assembleDriverPack" })
}

tasks.register<Sync>("syncAllDriverPacks") {
    group = "driver packs"
    description = "Copy assembled driver packs to build/driver-packs"
    dependsOn("assembleAllDriverPacks")
    into(layout.buildDirectory.dir("driver-packs"))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    driverPackProjects.forEach { project ->
        from(project.layout.buildDirectory.dir("driver-pack")) {
            include("**/*")
        }
    }
}
