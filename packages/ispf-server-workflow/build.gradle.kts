plugins {
    `java-library`
    id("io.spring.dependency-management")
}

// Workflow runtime. Package name stays com.ispf.server.workflow so component scan and existing
// imports do not change. This module must not depend on ispf-server.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

dependencies {
    api(project(":packages:ispf-core"))
    api(project(":packages:ispf-expression"))
    api(project(":packages:ispf-plugin-workflow"))
    api(project(":packages:ispf-persistence"))
    api(project(":packages:ispf-server-spi"))
    api(project(":packages:ispf-server-config"))
    api(project(":packages:ispf-ai-api"))
    api(project(":packages:ispf-ai-openai-compatible"))

    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-jackson")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
}
