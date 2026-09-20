dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // Pin (ADR-0059 registry): Milo/Moquette declare Netty 4.1.x; lift to the patched 4.2 line.
    testImplementation(enforcedPlatform("io.netty:netty-bom:4.2.17.Final"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.moquette:moquette-broker:0.17")
}
