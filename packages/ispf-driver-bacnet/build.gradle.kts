dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.infiniteautomation:bacnet4j:6.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
