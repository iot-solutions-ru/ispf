dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation(project(":packages:ispf-driver-iec104-server"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
