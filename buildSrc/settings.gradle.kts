pluginManagement {
    repositories {
        // kotlin-stdlib for kotlin-dsl lives on Maven Central; Plugin Portal alone flakes in CI.
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "buildSrc"
