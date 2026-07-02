dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("fr.jrds:vxIPMI:2.0.0.1")
    implementation("log4j:log4j:1.2.17")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
