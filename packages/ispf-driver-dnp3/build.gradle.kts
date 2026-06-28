dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))
    implementation("io.stepfunc:dnp3:1.6.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
