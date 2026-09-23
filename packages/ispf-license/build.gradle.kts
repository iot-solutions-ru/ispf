plugins {
    `java-library`
    id("io.spring.dependency-management")
}

// Commercial license checks and version comparison. Package names stay under com.ispf.server
// so component scan and existing imports do not change. This module must not depend on ispf-server.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-jackson")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
