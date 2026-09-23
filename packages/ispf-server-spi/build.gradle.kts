plugins {
    `java-library`
}

// Ports that break the object ↔ driver and object ↔ workflow cycles.
// This module must not depend on ispf-server.
dependencies {
    api(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
