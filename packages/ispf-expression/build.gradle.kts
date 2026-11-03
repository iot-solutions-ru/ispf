dependencies {
    implementation(project(":packages:ispf-core"))

    // CEL 0.15 gencode requires protobuf-java runtime >= 4.36.2 (aligned in root + Boot extra).
    implementation("dev.cel:cel:0.15.0")
    // Formal verification (Z3 via z3-turnkey) — AI condition safety gate.
    implementation("dev.cel:verifier:0.15.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
