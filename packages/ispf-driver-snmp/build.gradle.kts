dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.snmp4j:snmp4j:3.13.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
