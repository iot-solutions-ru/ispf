plugins {
    `java-library`
    id("io.spring.dependency-management")
}

// Driver runtime. Package name stays com.ispf.server.driver so component scan and existing
// imports do not change. This module must not depend on ispf-server.
// Keep in sync with ispf-server (Boot 4 Jackson BOM properties / nightly Trivy).
extra["jackson-2-bom.version"] = "2.22.3"
extra["jackson-bom.version"] = "3.2.3"
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    api(project(":packages:ispf-core"))
    api(project(":packages:ispf-driver-api"))
    api(project(":packages:ispf-server-spi"))
    api(project(":packages:ispf-server-config"))
    api(project(":packages:ispf-license"))
    api(project(":packages:ispf-concurrent"))

    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jackson")
    api("io.micrometer:micrometer-core")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
