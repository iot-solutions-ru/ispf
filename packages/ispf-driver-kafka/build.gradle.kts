dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.apache.kafka:kafka-clients:4.3.1")

    testImplementation("io.github.embeddedkafka:embedded-kafka_2.13:4.3.1")
    testRuntimeOnly("org.scala-lang:scala-library:2.13.15")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
