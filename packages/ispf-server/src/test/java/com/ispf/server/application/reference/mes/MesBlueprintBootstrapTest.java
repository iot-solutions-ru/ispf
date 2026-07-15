package com.ispf.server.application.reference.mes;

import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.core.object.ObjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MesBlueprintBootstrapTest {

    @Autowired
    private BlueprintRegistry blueprintRegistry;

    @Autowired
    private MesBlueprintBootstrap mesBlueprintBootstrap;

    @BeforeEach
    void seedMesModelsForTest() {
        mesBlueprintBootstrap.ensureMesModels();
    }

    @Test
    void batchV1RegisteredAsInstanceTypeForLots() {
        var model = blueprintRegistry.requireByName(MesBlueprintBootstrap.BATCH_MODEL);
        assertThat(model.type()).isEqualTo(BlueprintType.INSTANCE);
        assertThat(model.targetObjectType()).isEqualTo(ObjectType.LOT);
        assertThat(model.variables()).extracting(v -> v.name())
                .contains("batchId", "recipe", "phase");
    }

    @Test
    void workOrderV1RegisteredAsInstanceTypeForWorkOrders() {
        var model = blueprintRegistry.requireByName(MesBlueprintBootstrap.WORK_ORDER_MODEL);
        assertThat(model.type()).isEqualTo(BlueprintType.INSTANCE);
        assertThat(model.targetObjectType()).isEqualTo(ObjectType.WORK_ORDER);
        assertThat(model.variables()).extracting(v -> v.name())
                .contains("orderNumber", "lineCode", "status", "priority");
    }
}
