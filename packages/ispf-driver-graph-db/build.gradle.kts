dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    // Pin (ADR-0059 registry): neo4j-bolt-connection-netty declares Netty 4.2.15 (CVE-2026-75595); lift to 4.2.18.
    implementation(enforcedPlatform("io.netty:netty-bom:4.2.18.Final"))
    implementation("org.neo4j.driver:neo4j-java-driver:6.2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
