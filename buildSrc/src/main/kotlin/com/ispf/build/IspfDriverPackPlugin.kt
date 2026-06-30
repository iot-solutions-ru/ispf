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

data class ExternalDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val licenseType: String,
    val notice: String,
)

private val FAT_JAR_EXCLUDED_GROUPS = mapOf(
    "ispf-driver-dnp3" to setOf("io.stepfunc"),
)

private val EXTERNAL_DEPENDENCIES = mapOf(
    "ispf-driver-dnp3" to listOf(
        ExternalDependency(
            groupId = "io.stepfunc",
            artifactId = "dnp3",
            version = "1.6.0",
            licenseType = "LicenseRef-StepFunc-NonCommercial",
            notice = "Not bundled in this pack. Non-production evaluation only under Step Function I/O terms; " +
                "production/commercial use requires a separate license from https://stepfunc.io/contact",
        ),
    ),
)

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
            val excludedGroups = FAT_JAR_EXCLUDED_GROUPS[meta.packId].orEmpty()
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
                            !file.name.startsWith("ispf-driver-api") &&
                            excludedGroups.none { prefix -> file.name.startsWith(prefix) }
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
                projectName.set(project.name)
            }
        }
    }
}

abstract class AssembleDriverPackTask : DefaultTask() {
    @get:Input
    abstract val packMeta: Property<DriverPackMeta>

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val platformVersion: Property<String>

    @get:InputFile
    abstract val packJar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun assemble() {
        val meta = packMeta.get()
        val module = projectName.get()
        val out = outputDir.get().asFile
        out.mkdirs()

        Files.copy(
            packJar.get().asFile.toPath(),
            out.resolve(meta.jarFile).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        val manifest = linkedMapOf<String, Any>(
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

        val external = EXTERNAL_DEPENDENCIES[module]
        if (!external.isNullOrEmpty()) {
            manifest["externalDependencies"] = external.map {
                mapOf(
                    "groupId" to it.groupId,
                    "artifactId" to it.artifactId,
                    "version" to it.version,
                    "licenseType" to it.licenseType,
                    "notice" to it.notice,
                )
            }
        }

        out.resolve("driver-pack.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)))

        val root = project.rootProject.projectDir
        val licenseFile = resolveLicenseFile(root, meta.licenseType)
        if (licenseFile.isFile) {
            Files.copy(licenseFile.toPath(), out.resolve("LICENSE").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        writeThirdPartyNotice(out, meta, external)
        if (!external.isNullOrEmpty()) {
            writeExternalDepsNotice(out, external)
        }
    }

    private fun resolveLicenseFile(root: java.io.File, licenseType: String): java.io.File = when {
        licenseType.startsWith("GPL") || licenseType.startsWith("LGPL") || licenseType == "MPL-2.0" ->
            root.resolve("LICENSE-DRIVER-COPYLEFT.txt")
        licenseType.startsWith("LicenseRef-StepFunc") ->
            root.resolve("LICENSE-DRIVER-PROPRIETARY.txt")
        licenseType.startsWith("LicenseRef-NIST") || licenseType.contains("PublicDomain", ignoreCase = true) ->
            root.resolve("LICENSE-DRIVER-PUBLIC-DOMAIN.txt")
        else -> root.resolve("LICENSE-DRIVER-APACHE-2.0.txt")
    }

    private fun writeThirdPartyNotice(out: java.io.File, meta: DriverPackMeta, external: List<ExternalDependency>?) {
        val lines = buildList {
            add("ISPF Driver Pack: ${meta.packId}")
            add("licenseType: ${meta.licenseType}")
            add("")
            add("Bundled JAR: ${meta.jarFile}")
            add("See docs/THIRD_PARTY_NOTICES.md in the ISPF repository for dependency inventory.")
            if (!external.isNullOrEmpty()) {
                add("")
                add("External dependencies (NOT bundled in ${meta.jarFile}):")
                external.forEach { dep ->
                    add("- ${dep.groupId}:${dep.artifactId}:${dep.version} (${dep.licenseType})")
                    add("  ${dep.notice}")
                }
            }
        }
        out.resolve("THIRD_PARTY-NOTICE.txt").writeText(lines.joinToString("\n") + "\n")
    }

    private fun writeExternalDepsNotice(out: java.io.File, external: List<ExternalDependency>) {
        val lines = buildList {
            add("External dependencies required at runtime (not redistributed in this pack):")
            add("")
            external.forEach { dep ->
                add("${dep.groupId}:${dep.artifactId}:${dep.version}")
                add("License: ${dep.licenseType}")
                add(dep.notice)
                add("")
            }
        }
        out.resolve("NOTICE-EXTERNAL-DEPS.txt").writeText(lines.joinToString("\n") + "\n")
    }
}
