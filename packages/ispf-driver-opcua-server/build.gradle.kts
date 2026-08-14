dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation(enforcedPlatform("io.netty:netty-bom:4.2.17.Final"))
    implementation("org.eclipse.milo:sdk-server:0.6.16")

    testImplementation("org.eclipse.milo:sdk-client:0.6.16")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
