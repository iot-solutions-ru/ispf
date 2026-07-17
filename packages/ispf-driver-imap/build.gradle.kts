dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.angus:angus-mail:2.0.5")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
