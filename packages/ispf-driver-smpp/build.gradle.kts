dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.jsmpp:jsmpp:3.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
