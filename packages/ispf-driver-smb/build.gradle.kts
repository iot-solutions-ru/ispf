dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.hierynomus:smbj:0.13.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
