package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypedBlueprintFacadeTest {

    private static final String HUB_PATH = "root.platform.singleton-blueprints.parity-hub-v1";

    @Mock
    private BlueprintRegistry blueprintRegistry;
    @Mock
    private BlueprintEngine blueprintEngine;
    @Mock
    private BlueprintPersistenceService blueprintPersistence;
    @Mock
    private BlueprintApplicationService blueprintApplicationService;
    @Mock
    private ObjectManager objectManager;

    private TypedBlueprintFacade facade;

    @BeforeEach
    void setUp() {
        facade = new TypedBlueprintFacade(
                BlueprintType.SINGLETON,
                blueprintRegistry,
                blueprintEngine,
                blueprintPersistence,
                blueprintApplicationService,
                objectManager
        );
    }

    @Test
    void singletonInstanceUsesTheSamePathAsTheAgent() {
        BlueprintDefinition model = hubBlueprint();
        PlatformObject hub = new PlatformObject("1", HUB_PATH, ObjectType.CUSTOM, "Parity hub", "", null);
        when(blueprintRegistry.requireById(model.id())).thenReturn(model);
        when(blueprintApplicationService.ensureSingletonInstanceWithRules(model.id())).thenReturn(hub);

        assertThat(facade.singletonInstance(model.id())).isSameAs(hub);
        verify(blueprintEngine, never()).ensureSingletonInstance(any());
    }

    @Test
    void singletonInstanceReportsContributionFailureAsBadRequest() {
        BlueprintDefinition model = hubBlueprint();
        when(blueprintRegistry.requireById(model.id())).thenReturn(model);
        when(blueprintApplicationService.ensureSingletonInstanceWithRules(model.id()))
                .thenThrow(new IllegalArgumentException("alert template is invalid"));

        assertThatThrownBy(() -> facade.singletonInstance(model.id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("alert template is invalid");
                });
    }

    private static BlueprintDefinition hubBlueprint() {
        Instant now = Instant.now();
        return new BlueprintDefinition(
                "bp-hub",
                "parity-hub-v1",
                "hub",
                BlueprintType.SINGLETON,
                null,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                now,
                now
        );
    }
}
