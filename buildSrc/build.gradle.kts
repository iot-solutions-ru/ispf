plugins {
    `kotlin-dsl`
}

repositories {
    // kotlin-stdlib for kotlin-dsl is on Maven Central; Plugin Portal alone can fail resolution (CI).
    mavenCentral()
    gradlePluginPortal()
}
