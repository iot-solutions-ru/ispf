dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.milo:sdk-client:0.6.15")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
