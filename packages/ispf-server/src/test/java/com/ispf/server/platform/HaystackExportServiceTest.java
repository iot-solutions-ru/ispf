package com.ispf.server.platform;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HaystackExportServiceTest {

    private static final String DEVICE_PATH = "root.platform.devices.acl-export-test";
    private static final Authentication OPERATOR = UsernamePasswordAuthenticationToken.authenticated(
            "operator",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_operator"))
    );

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectAccessService objectAccessService;

    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    @Test
    void acceptsPathsUnderRoot() {
        assertTrue(HaystackExportService.isUnderRoot(
                "root.platform.devices.lab-userA-01",
                "root.platform.devices"
        ));
        assertTrue(HaystackExportService.isUnderRoot(
                "root.platform.devices",
                "root.platform.devices"
        ));
        assertFalse(HaystackExportService.isUnderRoot(
                "root.platform.reports.demo",
                "root.platform.devices"
        ));
    }

    @Test
    void normalizesBlankRootToPlatformDefault() {
        assertTrue(HaystackExportService.normalizeRootPath(null)
                .startsWith("root.platform"));
        assertTrue(HaystackExportService.normalizeRootPath("  ")
                .startsWith("root.platform"));
    }

    @Test
    void tagsMatchRequiresAllMarkers() {
        Map<String, Boolean> tags = Map.of(
                "equip", true,
                "point", true,
                "temp", true
        );
        assertTrue(HaystackExportService.tagsMatch(tags, List.of("equip", "temp")));
        assertFalse(HaystackExportService.tagsMatch(tags, List.of("equip", "power")));
    }

    @Test
    void normalizeTagQuerySplitsCommaSeparatedValues() {
        assertEquals(
                List.of("equip", "point", "temp"),
                HaystackExportService.normalizeTagQuery(List.of("equip,point", "temp"))
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void omitsPointWhenVariableReadIsDenied() {
        DataSchema stringSchema = DataSchema.builder("string")
                .field("value", FieldType.STRING)
                .build();
        DataSchema numericSchema = DataSchema.builder("numeric")
                .field("value", FieldType.DOUBLE)
                .build();
        PlatformObject device = new PlatformObject(
                "acl-export-test",
                DEVICE_PATH,
                ObjectType.DEVICE,
                "ACL export test",
                "",
                null
        );
        device.addVariable(new Variable(
                "haystackTags",
                stringSchema,
                true,
                true,
                DataRecord.single(stringSchema, Map.of("value", "[\"equip\"]"))
        ));
        device.addVariable(new Variable(
                "visibleTemperature",
                numericSchema,
                true,
                false,
                DataRecord.single(numericSchema, Map.of("value", 21.5)),
                true,
                null,
                List.of("operator"),
                List.of()
        ));
        device.addVariable(new Variable(
                "restrictedTemperature",
                numericSchema,
                true,
                false,
                DataRecord.single(numericSchema, Map.of("value", 99.0)),
                true,
                null,
                List.of("developer"),
                List.of()
        ));
        ObjectTree tree = new ObjectTree();
        tree.register(device);

        when(objectManager.tree()).thenReturn(tree);
        when(objectAccessService.canRead(DEVICE_PATH, OPERATOR)).thenReturn(true);
        when(variableMemberAccessService.canRead(DEVICE_PATH, "haystackTags", OPERATOR)).thenReturn(true);
        when(variableMemberAccessService.canRead(DEVICE_PATH, "visibleTemperature", OPERATOR)).thenReturn(true);
        when(variableMemberAccessService.canRead(DEVICE_PATH, "restrictedTemperature", OPERATOR)).thenReturn(false);

        HaystackExportService service = new HaystackExportService(
                objectManager,
                new ObjectMapper(),
                objectAccessService,
                variableMemberAccessService
        );
        Map<String, Object> payload = service.exportSubtree(OPERATOR, DEVICE_PATH, true);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");

        assertTrue(rows.stream().anyMatch(row -> "visibleTemperature".equals(row.get("variableName"))));
        assertFalse(rows.stream().anyMatch(row -> "restrictedTemperature".equals(row.get("variableName"))));
        assertFalse(payload.toString().contains("restrictedTemperature"));
    }
}
