dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    // Pin (ADR-0059 registry): Milo/Moquette declare Netty 4.1.x; lift to the patched 4.2 line.
    implementation(enforcedPlatform("io.netty:netty-bom:4.2.17.Final"))
    implementation("org.eclipse.milo:sdk-server:0.6.16")

    testImplementation("org.eclipse.milo:sdk-client:0.6.16")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
