plugins {
    `java-library`
    id("io.spring.dependency-management")
}

// Configuration properties used by driver and workflow. Package names stay com.ispf.server.config.
// This module must not depend on ispf-server.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    api(project(":packages:ispf-driver-api"))
    api("org.springframework.boot:spring-boot-starter")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
