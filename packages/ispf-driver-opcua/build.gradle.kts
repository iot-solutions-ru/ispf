dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation(enforcedPlatform("io.netty:netty-bom:4.1.135.Final"))
    implementation("org.eclipse.milo:sdk-client:0.6.16")

    testImplementation(project(":packages:ispf-driver-opcua-server"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
