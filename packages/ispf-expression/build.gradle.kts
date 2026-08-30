dependencies {
    implementation(project(":packages:ispf-core"))

    // CEL 0.14+ gencode requires protobuf-java runtime >= 4.35.1 (aligned in root build).
    implementation("dev.cel:cel:0.14.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
