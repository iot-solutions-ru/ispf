dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.apache.kafka:kafka-clients:3.8.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
