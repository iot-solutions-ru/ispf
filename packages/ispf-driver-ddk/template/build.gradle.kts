plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

val ispfVersion: String = providers.gradleProperty("ispfVersion").get()
val packId = "ispf-driver-acme-widget"

dependencies {
    implementation("com.ispf:ispf-driver-api:$ispfVersion")
    implementation("com.ispf:ispf-core:$ispfVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("assembleDriverPack") {
    group = "driver packs"
    description = "Copy driver-pack.json and the jar into build/driver-pack/$packId"
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    from("driver-pack.json")
    into(layout.buildDirectory.dir("driver-pack/$packId"))
    rename { name -> if (name.endsWith(".jar")) "$packId.jar" else name }
}
