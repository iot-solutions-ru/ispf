dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.openmuc:j60870:1.7.2")

    testImplementation(project(":packages:ispf-driver-iec104-server"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
