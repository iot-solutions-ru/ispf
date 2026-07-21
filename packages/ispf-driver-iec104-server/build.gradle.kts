dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.openmuc:j60870:1.8.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
