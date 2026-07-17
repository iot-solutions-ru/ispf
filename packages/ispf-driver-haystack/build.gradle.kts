dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
