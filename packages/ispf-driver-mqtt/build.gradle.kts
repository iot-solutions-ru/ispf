dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.moquette:moquette-broker:0.17")
}
