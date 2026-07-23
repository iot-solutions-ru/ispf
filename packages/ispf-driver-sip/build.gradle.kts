dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("javax.sip:jain-sip-ri:1.3.0-91")

    // jain-sip-ri declares log4j 1.2 as "provided" but loads org.apache.log4j.Layout at stack
    // init; reload4j is the repo-standard drop-in (see ispf-driver-ipmi). Runtime dependency:
    // connect() creates the SipStack, so the driver pack needs the log4j-1.2 API on the classpath.
    implementation("ch.qos.reload4j:reload4j:1.2.26")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}
