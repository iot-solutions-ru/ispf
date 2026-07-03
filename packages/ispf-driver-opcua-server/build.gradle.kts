dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation(enforcedPlatform("io.netty:netty-bom:4.1.135.Final"))
    implementation("org.eclipse.milo:sdk-server:0.6.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
