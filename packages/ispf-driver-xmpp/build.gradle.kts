dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.igniterealtime.smack:smack-tcp:4.4.8")
    implementation("org.igniterealtime.smack:smack-im:4.4.8")
    implementation("org.igniterealtime.smack:smack-extensions:4.4.8")
    // Runtime requirements of Smack 4.4: without an XML parser and the java8
    // initializers (Base64 encoder) any client use fails with ExceptionInInitializerError —
    // the driver pack must ship these, not just the tests.
    implementation("org.igniterealtime.smack:smack-xmlparser-xpp3:4.4.8")
    implementation("org.igniterealtime.smack:smack-java8:4.4.8")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
