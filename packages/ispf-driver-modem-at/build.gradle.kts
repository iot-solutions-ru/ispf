dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.fazecast:jSerialComm:2.11.4")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
