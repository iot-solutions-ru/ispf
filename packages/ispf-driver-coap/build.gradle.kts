dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.californium:californium-core:3.14.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
