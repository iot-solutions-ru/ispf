plugins {
    `java-library`
    id("io.spring.dependency-management")
}

// Elastic thread pools. Package name stays com.ispf.server.concurrent so existing imports
// do not change. This module must not depend on ispf-server.
// Keep in sync with ispf-server (Boot 4 Jackson BOM properties / nightly Trivy).
extra["jackson-2-bom.version"] = "2.22.3"
extra["jackson-bom.version"] = "3.2.3"
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
