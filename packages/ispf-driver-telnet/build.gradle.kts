dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("commons-net:commons-net:3.11.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
