dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.openmuc:jmbus:3.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
