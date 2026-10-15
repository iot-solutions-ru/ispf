dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}

sourceSets {
    main {
        java {
            srcDir("template/src/main/java")
            srcDir("examples/simple-counter/src/main/java")
            srcDir("examples/json-poller/src/main/java")
            srcDir("examples/modbus-simulator/src/main/java")
        }
    }
    test {
        java {
            srcDir("template/src/test/java")
            srcDir("examples/simple-counter/src/test/java")
            srcDir("examples/json-poller/src/test/java")
            srcDir("examples/modbus-simulator/src/test/java")
        }
    }
}

val ddkExamplePacks = listOf(
    "simple-counter" to "com.ispf.driver.simplecounter",
    "json-poller" to "com.ispf.driver.jsonpoller",
    "modbus-simulator" to "com.ispf.driver.modbussimulator",
)

tasks.register("assembleDdkExamplePacks") {
    group = "driver packs"
    description = "Assemble BL-144 reference driver packs under build/ddk-example-packs/"
    dependsOn(tasks.named("jar"))
    doLast {
        val jarTask = tasks.named("jar", Jar::class).get()
        val jarFile = jarTask.archiveFile.get().asFile
        val outRoot = layout.buildDirectory.dir("ddk-example-packs").get().asFile
        outRoot.mkdirs()
        for ((exampleId, _) in ddkExamplePacks) {
            val packJson = project.file("examples/$exampleId/driver-pack.json")
            require(packJson.exists()) { "missing $packJson" }
            val packDir = outRoot.resolve("ispf-driver-$exampleId")
            packDir.mkdirs()
            packJson.copyTo(packDir.resolve("driver-pack.json"), overwrite = true)
            jarFile.copyTo(packDir.resolve("ispf-driver-$exampleId.jar"), overwrite = true)
        }
    }
}

tasks.named("check") {
    dependsOn("assembleDdkExamplePacks")
}
