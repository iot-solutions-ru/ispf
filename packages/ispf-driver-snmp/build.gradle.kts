dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.snmp4j:snmp4j:3.12.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
