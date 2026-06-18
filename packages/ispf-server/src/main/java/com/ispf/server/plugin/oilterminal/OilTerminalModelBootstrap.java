package com.ispf.server.plugin.oilterminal;

import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.oilterminal.OilTerminalModelDefinitions;
import org.springframework.stereotype.Component;

/**
 * Registers oil terminal models in the in-memory model registry on startup.
 */
@Component
public class OilTerminalModelBootstrap {

    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public OilTerminalModelBootstrap(ModelEngine modelEngine, ModelRegistry modelRegistry) {
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    public void ensureModels() {
        if (modelRegistry.findByName(com.ispf.plugin.oilterminal.OilTerminalConstants.MODEL_TANK).isPresent()) {
            return;
        }
        modelEngine.createModel(OilTerminalModelDefinitions.oilTank());
        modelEngine.createModel(OilTerminalModelDefinitions.oilRack());
        modelEngine.createModel(OilTerminalModelDefinitions.dispatchOrder());
        modelEngine.createModel(OilTerminalModelDefinitions.oilSample());
    }
}
