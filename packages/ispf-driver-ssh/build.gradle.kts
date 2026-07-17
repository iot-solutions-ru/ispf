dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.github.mwiede:jsch:2.28.4")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
