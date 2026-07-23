dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("com.h2database:h2:2.4.240")
}
