dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.apache.kafka:kafka-clients:3.9.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
