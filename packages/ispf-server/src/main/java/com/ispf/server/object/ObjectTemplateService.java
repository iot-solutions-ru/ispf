package com.ispf.server.object;

import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintException;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies model templates ({@code templateId}) to objects after creation.
 */
@Service
public class ObjectTemplateService {

    private static final Map<String, List<String>> COMPANION_MODELS_BY_NAME = Map.of(
            LabBlueprintBootstrap.VIRTUAL_LAB_MODEL,
            List.of(LabBlueprintBootstrap.VIRTUAL_LAB_WAVES_SUM_MODEL),
            LabBlueprintBootstrap.VIRTUAL_UNIFIED_MODEL,
            List.of(LabBlueprintBootstrap.VIRTUAL_LAB_WAVES_SUM_MODEL)
    );

    private final BlueprintRegistry BlueprintRegistry;
    private final BlueprintApplicationService BlueprintApplicationService;

    public ObjectTemplateService(
            BlueprintRegistry BlueprintRegistry,
            BlueprintApplicationService BlueprintApplicationService
    ) {
        this.BlueprintRegistry = BlueprintRegistry;
        this.BlueprintApplicationService = BlueprintApplicationService;
    }

    @Transactional
    public void applyTemplate(String objectPath, String templateId) {
        if (templateId == null || templateId.isBlank()) {
            return;
        }
        BlueprintDefinition model = resolveTemplate(templateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown templateId: " + templateId
                ));
        applyResolvedModel(objectPath, model);
        for (String companionName : COMPANION_MODELS_BY_NAME.getOrDefault(model.name(), List.of())) {
            BlueprintRegistry.findByName(companionName).ifPresent(companion ->
                    applyResolvedModel(objectPath, companion)
            );
        }
    }

    private void applyResolvedModel(String objectPath, BlueprintDefinition model) {
        try {
            BlueprintApplicationService.applyBlueprintWithRules(model, objectPath, model.parameters());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Optional<BlueprintDefinition> resolveTemplate(String templateId) {
        Optional<BlueprintDefinition> byId = BlueprintRegistry.findById(templateId);
        if (byId.isPresent()) {
            return byId;
        }
        return BlueprintRegistry.findByName(templateId);
    }
}
