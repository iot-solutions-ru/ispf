dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    // vxIPMI drags in EOL log4j 1.2.14 (CVE-2019-17571); reload4j is the maintained drop-in.
    implementation("fr.jrds:vxIPMI:2.0.0.1") {
        exclude(group = "log4j", module = "log4j")
    }
    implementation("ch.qos.reload4j:reload4j:1.2.26")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
