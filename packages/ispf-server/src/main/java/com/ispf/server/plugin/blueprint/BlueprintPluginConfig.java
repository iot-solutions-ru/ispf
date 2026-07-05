package com.ispf.server.plugin.blueprint;

import com.ispf.expression.ExpressionEngine;
import com.ispf.core.object.ObjectTree;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlueprintPluginConfig {

    @Bean
    BlueprintRegistry blueprintRegistry() {
        return new BlueprintRegistry();
    }

    @Bean
    ExpressionEngine expressionEngine() {
        return new ExpressionEngine();
    }

    @Bean
    BlueprintEngine blueprintEngine(
            BlueprintRegistry blueprintRegistry,
            ObjectTree objectTree,
            ExpressionEngine expressionEngine
    ) {
        return new BlueprintEngine(blueprintRegistry, objectTree, expressionEngine);
    }

    @Bean
    TypedBlueprintFacade relativeBlueprintFacade(
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine,
            BlueprintPersistenceService blueprintPersistence,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager
    ) {
        return new TypedBlueprintFacade(
                BlueprintType.RELATIVE,
                blueprintRegistry,
                blueprintEngine,
                blueprintPersistence,
                blueprintApplicationService,
                objectManager
        );
    }

    @Bean
    TypedBlueprintFacade instanceTypeFacade(
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine,
            BlueprintPersistenceService blueprintPersistence,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager
    ) {
        return new TypedBlueprintFacade(
                BlueprintType.INSTANCE,
                blueprintRegistry,
                blueprintEngine,
                blueprintPersistence,
                blueprintApplicationService,
                objectManager
        );
    }

    @Bean
    TypedBlueprintFacade absoluteBlueprintFacade(
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine,
            BlueprintPersistenceService blueprintPersistence,
            BlueprintApplicationService blueprintApplicationService,
            ObjectManager objectManager
    ) {
        return new TypedBlueprintFacade(
                BlueprintType.ABSOLUTE,
                blueprintRegistry,
                blueprintEngine,
                blueprintPersistence,
                blueprintApplicationService,
                objectManager
        );
    }
}
