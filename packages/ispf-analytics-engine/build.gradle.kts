plugins {
    `java-library`
}

dependencies {
    api(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
