package com.ispf.server.plugin.model;

import com.ispf.expression.BindingEvaluator;
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
    BindingEvaluator bindingEvaluator() {
        return new BindingEvaluator();
    }

    @Bean
    ModelEngine modelEngine(
            ModelRegistry modelRegistry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine,
            BindingEvaluator bindingEvaluator
    ) {
        return new ModelEngine(modelRegistry, objectTree, expressionEngine, bindingEvaluator);
    }
}
