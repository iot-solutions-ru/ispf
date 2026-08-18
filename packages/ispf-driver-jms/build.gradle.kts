dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.apache.activemq:activemq-client:6.3.1")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.apache.activemq:activemq-broker:6.3.1")
}
