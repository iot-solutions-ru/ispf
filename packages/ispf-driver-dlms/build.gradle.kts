dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.gurux:gurux.dlms:4.0.95")
    implementation("org.gurux:gurux.net:1.0.32")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
