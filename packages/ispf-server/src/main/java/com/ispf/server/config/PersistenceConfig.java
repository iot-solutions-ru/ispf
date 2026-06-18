package com.ispf.server.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.ispf.server.persistence")
@EntityScan(basePackages = "com.ispf.server.persistence.entity")
public class PersistenceConfig {
}
