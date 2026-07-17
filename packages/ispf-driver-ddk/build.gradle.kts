dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
}

sourceSets {
    main {
        java {
            srcDir("template/src/main/java")
            srcDir("examples/simple-counter/src/main/java")
            srcDir("examples/json-poller/src/main/java")
            srcDir("examples/modbus-simulator/src/main/java")
        }
    }
    test {
        java {
            srcDir("template/src/test/java")
            srcDir("examples/simple-counter/src/test/java")
            srcDir("examples/json-poller/src/test/java")
            srcDir("examples/modbus-simulator/src/test/java")
        }
    }
}
