import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
}

dependencies {
    api(project(":packages:ispf-analytics-api"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

val generateAnalyticsPackManifest = tasks.register("generateAnalyticsPackManifest") {
    val outputDir = layout.buildDirectory.dir("generated/resources/analytics-pack")
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("analytics-pack.json").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            {
              "packId": "ispf-analytics-core-ext",
              "version": "${project.version}",
              "licenseType": "Apache-2.0"
            }
            """.trimIndent()
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    from(generateAnalyticsPackManifest)
}
