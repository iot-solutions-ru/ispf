dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.github.s7connector:s7connector:2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
