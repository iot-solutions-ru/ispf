plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenLocal {
        content {
            includeGroup("com.ispf")
        }
    }
    mavenCentral()
}

val ispfVersion: String = providers.gradleProperty("ispfVersion").get()
val packId = "ispf-driver-acme-widget"
val packJarName = "$packId.jar"

dependencies {
    // Platform SPI stays off the pack jar — the server already loads it.
    compileOnly("com.ispf:ispf-driver-api:$ispfVersion")
    compileOnly("com.ispf:ispf-core:$ispfVersion")
    testImplementation("com.ispf:ispf-driver-api:$ispfVersion")
    testImplementation("com.ispf:ispf-core:$ispfVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // Third-party libraries go on implementation so driverPackJar embeds them
    // (LicensedDriverPackLoader loads a single jarFile, not a lib/ directory).
}

tasks.test {
    useJUnitPlatform()
}

val driverPackJar = tasks.register<Jar>("driverPackJar") {
    group = "driver packs"
    description = "Fat jar for $packId (driver classes + third-party runtime deps)"
    archiveFileName.set(packJarName)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.jar)
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { file ->
            file.isFile &&
                !file.name.startsWith("ispf-core") &&
                !file.name.startsWith("ispf-driver-api")
        }.map { file ->
            zipTree(file).matching {
                exclude(
                    "META-INF/*.SF",
                    "META-INF/*.RSA",
                    "META-INF/*.DSA",
                    "META-INF/*.EC",
                    "META-INF/*.sf",
                    "META-INF/*.rsa",
                    "META-INF/*.dsa",
                    "META-INF/*.ec",
                    "META-INF/SIG-*",
                )
            }
        }
    })
}

tasks.register<Copy>("assembleDriverPack") {
    group = "driver packs"
    description = "Copy driver-pack.json and the fat jar into build/driver-pack/$packId"
    dependsOn(driverPackJar)
    from(driverPackJar.map { it.archiveFile })
    from("driver-pack.json")
    into(layout.buildDirectory.dir("driver-pack/$packId"))
}
