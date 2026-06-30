dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
