package com.ispf.server.plugin.model;

import com.ispf.expression.ExpressionEngine;
import com.ispf.core.object.ObjectTree;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelPluginConfig {

    @Bean
    ModelRegistry modelRegistry() {
        return new ModelRegistry();
    }

    @Bean
    ExpressionEngine expressionEngine() {
        return new ExpressionEngine();
    }

    @Bean
    ModelEngine modelEngine(
            ModelRegistry modelRegistry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine
    ) {
        return new ModelEngine(modelRegistry, objectTree, expressionEngine);
    }
}
