dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.neo4j.driver:neo4j-java-driver:6.2.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
