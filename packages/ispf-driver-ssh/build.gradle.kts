dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.github.mwiede:jsch:0.2.21")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
