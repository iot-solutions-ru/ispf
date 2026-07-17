import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
}

dependencies {
    api(project(":packages:ispf-analytics-api"))
    implementation(project(":packages:ispf-analytics-engine"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

val packId = "ispf-analytics-kpi-demo"
val packJarName = "$packId.jar"

val generateAnalyticsPackManifest = tasks.register("generateAnalyticsPackManifest") {
    val outputDir = layout.buildDirectory.dir("generated/resources/analytics-pack")
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("analytics-pack.json").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            {
              "packId": "$packId",
              "version": "${project.version}",
              "licenseType": "Apache-2.0",
              "minPlatformVersion": "0.9.127",
              "jarFile": "$packJarName",
              "functions": ["percentChange"]
            }
            """.trimIndent()
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    from(generateAnalyticsPackManifest)
}

tasks.register<Jar>("assembleAnalyticsPackJar") {
    dependsOn(tasks.jar)
    archiveFileName.set(packJarName)
    from(tasks.jar.get().outputs.files)
}

tasks.register<Copy>("assembleAnalyticsPackDir") {
    dependsOn(tasks.jar, generateAnalyticsPackManifest)
    into(layout.buildDirectory.dir("analytics-packs/$packId"))
    from(tasks.jar.get().outputs.files) {
        rename { packJarName }
    }
    from(generateAnalyticsPackManifest) {
        include("analytics-pack.json")
    }
    from(rootProject.file("LICENSE"))
}

tasks.register<Zip>("assembleAnalyticsMarketplaceZip") {
    val packDirTask = tasks.named<Copy>("assembleAnalyticsPackDir")
    dependsOn(packDirTask)
    archiveFileName.set("analytics-pack-demo-1.0.0.zip")
    destinationDirectory.set(rootProject.file("examples/marketplace-analytics-pack-demo"))
    from(packDirTask.map { it.destinationDir })
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
