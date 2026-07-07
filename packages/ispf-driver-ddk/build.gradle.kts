dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

sourceSets {
    main {
        java {
            srcDir("template/src/main/java")
        }
    }
    test {
        java {
            srcDir("template/src/test/java")
        }
    }
}
