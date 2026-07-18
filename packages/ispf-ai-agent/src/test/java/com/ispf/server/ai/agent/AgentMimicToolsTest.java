package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.mimic.MimicLayouts;
import com.ispf.server.mimic.MimicService;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentMimicToolsTest {

    @Mock
    private MimicService mimicService;
    @Mock
    private ObjectTreePort ObjectTreePort;
    @Mock
    private ObjectAccessService objectAccessService;
    @Mock
    private TenantScopeService tenantScopeService;
    @Mock
    private Authentication authentication;
    @Mock
    private PlatformObject mimicNode;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlatformAgentTool tool(String name) {
        return AgentMimicTools.all(
                mimicService, ObjectTreePort, objectAccessService, tenantScopeService, objectMapper
        ).stream().filter(t -> name.equals(t.name())).findFirst().orElseThrow();
    }

    @Test
    void saveMimicDiagramWithElementsShorthand() throws Exception {
        String path = "root.platform.mimics.demo";
        when(tenantScopeService.isPathVisible(path, authentication)).thenReturn(true);
        doNothing().when(objectAccessService).requireWrite(path, authentication);
        when(ObjectTreePort.require(path)).thenReturn(mimicNode);
        when(mimicNode.type()).thenReturn(ObjectType.MIMIC);
        when(mimicService.getMimic(path)).thenReturn(
                new MimicService.MimicView(path, "Demo", 5000, MimicLayouts.EMPTY_MIMIC)
        );
        when(mimicService.saveDiagram(eq(path), any())).thenAnswer(invocation -> {
            String diagramJson = invocation.getArgument(1);
            return new MimicService.MimicView(path, "Demo", 5000, diagramJson);
        });

        Map<String, Object> result = tool("save_mimic_diagram").execute(
                Map.of(
                        "path", path,
                        "elements", List.of(Map.of(
                                "id", "t1",
                                "symbolId", "tank.vertical",
                                "layerId", "layer-default",
                                "x", 100,
                                "y", 80
                        ))
                ),
                new AgentContext("admin", authentication, null)
        );

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("elementCount"));
        ArgumentCaptor<String> diagramCaptor = ArgumentCaptor.forClass(String.class);
        verify(mimicService).saveDiagram(eq(path), diagramCaptor.capture());
        assertTrue(diagramCaptor.getValue().contains("tank.vertical"));
    }

    @Test
    void saveMimicDiagramRejectsEmptyReplace() throws Exception {
        String path = "root.platform.mimics.demo";
        when(tenantScopeService.isPathVisible(path, authentication)).thenReturn(true);
        doNothing().when(objectAccessService).requireWrite(path, authentication);
        when(ObjectTreePort.require(path)).thenReturn(mimicNode);
        when(mimicNode.type()).thenReturn(ObjectType.MIMIC);

        Map<String, Object> result = tool("save_mimic_diagram").execute(
                Map.of("path", path, "elements", List.of()),
                new AgentContext("admin", authentication, null)
        );

        assertEquals("ERROR", result.get("status"));
        assertTrue(String.valueOf(result.get("error")).contains("non-empty"));
    }

    @Test
    void saveMimicDiagramEnrichesPumpBindings() throws Exception {
        String path = "root.platform.mimics.demo";
        String devicePath = "root.platform.devices.centrifugal-pump";
        when(tenantScopeService.isPathVisible(path, authentication)).thenReturn(true);
        doNothing().when(objectAccessService).requireWrite(path, authentication);
        when(ObjectTreePort.require(path)).thenReturn(mimicNode);
        when(mimicNode.type()).thenReturn(ObjectType.MIMIC);
        when(mimicService.getMimic(path)).thenReturn(
                new MimicService.MimicView(path, "Demo", 5000, MimicLayouts.EMPTY_MIMIC)
        );
        when(mimicService.saveDiagram(eq(path), any())).thenAnswer(invocation -> {
            String diagramJson = invocation.getArgument(1);
            return new MimicService.MimicView(path, "Demo", 5000, diagramJson);
        });

        Map<String, Object> result = tool("save_mimic_diagram").execute(
                Map.of(
                        "path", path,
                        "devicePath", devicePath,
                        "elements", List.of(Map.of(
                                "id", "p1",
                                "symbolId", "pump.centrifugal",
                                "layerId", "layer-default",
                                "x", 200,
                                "y", 120,
                                "bindings", Map.of(
                                        "running", Map.of("variableName", "vibration"),
                                        "fault", Map.of("variableName", "vibration")
                                )
                        ))
                ),
                new AgentContext("admin", authentication, null)
        );

        assertEquals("OK", result.get("status"));
        assertEquals(1, result.get("elementCount"));
        ArgumentCaptor<String> diagramCaptor = ArgumentCaptor.forClass(String.class);
        verify(mimicService).saveDiagram(eq(path), diagramCaptor.capture());
        String saved = diagramCaptor.getValue();
        assertTrue(saved.contains("\"variableName\":\"sineWave\""));
        assertTrue(saved.contains(devicePath));
        assertTrue(saved.contains("\"transform\":\"bool\""));
    }

    @Test
    void listMimicSymbolsReturnsCatalog() throws Exception {
        Map<String, Object> result = tool("list_mimic_symbols").execute(
                Map.of("category", "process"),
                new AgentContext("admin", authentication, null)
        );
        assertEquals("OK", result.get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> symbols = (List<Map<String, Object>>) result.get("symbols");
        assertTrue(symbols.stream().anyMatch(row -> "tank.vertical".equals(row.get("id"))));
    }
}
