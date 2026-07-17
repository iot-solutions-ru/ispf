dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    testImplementation(enforcedPlatform("io.netty:netty-bom:4.2.16.Final"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("io.moquette:moquette-broker:0.17")
}
