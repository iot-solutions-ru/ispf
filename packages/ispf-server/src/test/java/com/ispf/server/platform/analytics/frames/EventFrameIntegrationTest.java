package com.ispf.server.platform.analytics.frames;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.application.reference.mes.MesBlueprintBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-208: event frames for MES shift windows and ISA-88 batch phase changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventFrameIntegrationTest {

    private static final String BATCH_PATH = "root.platform.mes.lots.batch-line-a01-001";
    private static final String HUB = "root.platform.devices.mes-platform-hub";
    private static final String SEED_SHIFT_ID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private EventFrameService eventFrameService;

    @Autowired
    private BlueprintApplicationService blueprintApplicationService;

    @Autowired
    private BlueprintRegistry blueprintRegistry;

    @Test
    void batchPhaseVariableOpensAndClosesFrames() {
        deployMesBundle();
        ensureBatchLot();

        objectManager.setVariableValue(
                BATCH_PATH,
                "phase",
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", "charge")
                )
        );
        List<EventFrame> afterCharge = eventFrameService.listActive(BATCH_PATH);
        assertThat(afterCharge).hasSize(1);
        assertThat(afterCharge.getFirst().frameType()).isEqualTo(EventFrameType.BATCH);
        assertThat(afterCharge.getFirst().metadata()).containsEntry("phase", "charge");

        objectManager.setVariableValue(
                BATCH_PATH,
                "phase",
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", "react")
                )
        );
        List<EventFrame> afterReact = eventFrameService.listActive(BATCH_PATH);
        assertThat(afterReact).hasSize(1);
        assertThat(afterReact.getFirst().metadata()).containsEntry("phase", "react");
        assertThat(afterReact.getFirst().frameId()).isNotEqualTo(afterCharge.getFirst().frameId());
    }

    @Test
    void mesShiftFrameAndDowntimeReport() throws Exception {
        deployMesBundle();

        mockMvc.perform(post("/api/v1/platform/analytics/frames/open-shift")
                        .param("shiftId", SEED_SHIFT_ID)
                        .param("scopePath", HUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frameType").value("shift"))
                .andExpect(jsonPath("$.downtimeMinutes").value(45));

        mockMvc.perform(get("/api/v1/platform/analytics/frames/downtime-report")
                        .param("scopePath", HUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].downtimeMinutes").value(45))
                .andExpect(jsonPath("$[0].frameType").value("shift"));
    }

    private void ensureBatchLot() {
        if (objectManager.tree().findByPath(BATCH_PATH).isEmpty()) {
            objectManager.create(
                    "root.platform.mes.lots",
                    "batch-line-a01-001",
                    ObjectType.LOT,
                    "Batch line A01-001",
                    "",
                    MesBlueprintBootstrap.BATCH_MODEL
            );
        }
        var model = blueprintRegistry.findByName(MesBlueprintBootstrap.BATCH_MODEL).orElseThrow();
        blueprintApplicationService.applyBlueprintWithRules(model, BATCH_PATH, Map.of());
    }

    private void deployMesBundle() {
        try {
            String bundle = new ClassPathResource("mes-platform-bundle.json")
                    .getContentAsString(StandardCharsets.UTF_8);
            mockMvc.perform(post("/api/v1/applications/mes-platform/deploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bundle))
                    .andExpect(status().isOk());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
