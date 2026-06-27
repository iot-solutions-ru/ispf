package com.ispf.build

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class DriverPackMeta(
    val packId: String,
    val driverId: String,
    val driverClass: String,
    val licenseType: String,
    val jarFile: String,
) : java.io.Serializable

class IspfDriverPackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project.name == "ispf-driver-api" || !project.name.startsWith("ispf-driver-")) {
            return
        }

        val catalogFile = project.rootProject.file("gradle/driver-packs.json")
        @Suppress("UNCHECKED_CAST")
        val catalog = JsonSlurper().parse(catalogFile) as Map<String, Map<String, String>>
        val raw = catalog[project.name] ?: error("Missing driver pack metadata for ${project.name}")
        val meta = DriverPackMeta(
            packId = raw["packId"]!!,
            driverId = raw["driverId"]!!,
            driverClass = raw["driverClass"]!!,
            licenseType = raw["licenseType"]!!,
            jarFile = raw["jarFile"]!!,
        )

        project.afterEvaluate {
            val main = project.extensions.getByType<SourceSetContainer>().named(SourceSet.MAIN_SOURCE_SET_NAME)
            val driverPackJar = project.tasks.register<Jar>("driverPackJar") {
                group = "driver packs"
                description = "Build driver pack JAR for ${meta.packId}"
                archiveFileName.set(meta.jarFile)
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                dependsOn(project.tasks.named("jar"))

                from(main.map { it.output })
                from({
                    project.configurations.getByName("runtimeClasspath").filter { file ->
                        file.isFile &&
                            !file.name.startsWith("ispf-core") &&
                            !file.name.startsWith("ispf-driver-api")
                    }.map { file -> project.zipTree(file) }
                })
            }

            project.tasks.register<AssembleDriverPackTask>("assembleDriverPack") {
                group = "driver packs"
                description = "Assemble driver-pack directory for ${meta.packId}"
                dependsOn(driverPackJar)
                packMeta.set(meta)
                packJar.set(driverPackJar.flatMap { it.archiveFile })
                outputDir.set(project.layout.buildDirectory.dir("driver-pack/${meta.packId}"))
                platformVersion.set(project.version.toString())
            }
        }
    }
}

abstract class AssembleDriverPackTask : DefaultTask() {
    @get:Input
    abstract val packMeta: Property<DriverPackMeta>

    @get:Input
    abstract val platformVersion: Property<String>

    @get:InputFile
    abstract val packJar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun assemble() {
        val meta = packMeta.get()
        val out = outputDir.get().asFile
        out.mkdirs()

        Files.copy(
            packJar.get().asFile.toPath(),
            out.resolve(meta.jarFile).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        val manifest = linkedMapOf(
            "packId" to meta.packId,
            "driverId" to meta.driverId,
            "minPlatformVersion" to platformVersion.get(),
            "jarFile" to meta.jarFile,
            "licenseType" to meta.licenseType,
            "drivers" to listOf(
                mapOf(
                    "driverId" to meta.driverId,
                    "driverClass" to meta.driverClass,
                ),
            ),
        )
        out.resolve("driver-pack.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)))

        val root = project.rootProject.projectDir
        val licenseFile = when {
            meta.licenseType.startsWith("GPL") || meta.licenseType.startsWith("LGPL") || meta.licenseType == "MPL-2.0" ->
                root.resolve("LICENSE-DRIVER-COPYLEFT.txt")
            else -> root.resolve("LICENSE-DRIVER-APACHE-2.0.txt")
        }
        if (licenseFile.isFile) {
            Files.copy(licenseFile.toPath(), out.resolve("LICENSE").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
