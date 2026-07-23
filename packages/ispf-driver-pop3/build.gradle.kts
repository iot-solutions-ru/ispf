dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.angus:angus-mail:2.0.5")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    // Jakarta-based embedded mail server for loopback tests. The redundant Angus
    // "jakarta.mail" artifact is excluded: angus-mail (above) already ships the same classes.
    testImplementation("com.icegreen:greenmail:2.1.3") {
        exclude(group = "org.eclipse.angus", module = "jakarta.mail")
    }
}
