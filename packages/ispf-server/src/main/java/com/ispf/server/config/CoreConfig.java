package com.ispf.server.config;

import com.ispf.core.object.ObjectTree;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfig {

    @Bean
    ObjectTree objectTree(ObjectManager objectManager) {
        return objectManager.tree();
    }
}
