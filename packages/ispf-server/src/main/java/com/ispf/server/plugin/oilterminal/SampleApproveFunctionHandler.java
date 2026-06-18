package com.ispf.server.plugin.oilterminal;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.oilterminal.OilTerminalConstants;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionHandler;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class SampleApproveFunctionHandler implements FunctionHandler {

    private static final DataSchema LAB_PAYLOAD = DataSchema.builder("labApprovedPayload")
            .field("tankName", FieldType.STRING)
            .field("sampleNo", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final EventService eventService;

    public SampleApproveFunctionHandler(ObjectManager objectManager, EventService eventService) {
        this.objectManager = objectManager;
        this.eventService = eventService;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!"approve".equals(functionName)) {
            return false;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        return node != null && OilTerminalObjects.isOilSample(node);
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        boolean approved = OilTerminalObjects.booleanValue(objectManager, objectPath, "approved").orElse(false);
        if (approved) {
            return OilTerminalObjects.failure("Sample already approved");
        }

        String tankName = OilTerminalObjects.stringValue(objectManager, objectPath, "tankName").orElse("");
        if (tankName.isBlank() && input != null && !input.rows().isEmpty()) {
            Object fromInput = input.firstRow().get("tankName");
            if (fromInput != null) {
                tankName = fromInput.toString();
            }
        }
        if (tankName.isBlank()) {
            return OilTerminalObjects.failure("tankName is required");
        }

        String tankPath = OilTerminalConstants.tankPath(tankName);
        if (objectManager.tree().findByPath(tankPath).isEmpty()) {
            return OilTerminalObjects.failure("Tank not found: " + tankName);
        }

        OilTerminalObjects.setBoolean(objectManager, objectPath, "approved", true);
        OilTerminalObjects.setBoolean(objectManager, tankPath, "qualityOk", true);
        objectManager.persistNodeTree(objectPath);
        objectManager.persistNodeTree(tankPath);

        String sampleNo = OilTerminalObjects.stringValue(objectManager, objectPath, "sampleNo").orElse("");
        eventService.fire(objectPath, OilTerminalConstants.EVENT_LAB_APPROVED, DataRecord.single(LAB_PAYLOAD, Map.of(
                "tankName", tankName,
                "sampleNo", sampleNo
        )));

        return OilTerminalObjects.success("Tank " + tankName + " approved");
    }
}
