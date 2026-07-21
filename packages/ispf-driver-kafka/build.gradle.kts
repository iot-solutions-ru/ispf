dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.apache.kafka:kafka-clients:4.3.1")

    testImplementation("io.github.embeddedkafka:embedded-kafka_2.13:3.9.0")
    testRuntimeOnly("org.scala-lang:scala-library:3.8.4")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
