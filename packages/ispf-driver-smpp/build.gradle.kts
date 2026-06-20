dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.jsmpp:jsmpp:3.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
