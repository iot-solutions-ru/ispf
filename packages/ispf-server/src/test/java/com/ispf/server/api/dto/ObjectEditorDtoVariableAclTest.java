package com.ispf.server.api.dto;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectEditorDtoVariableAclTest {

    @Test
    void editorOmitsRestrictedVariablesAndTheirNames() {
        String path = "root.platform.devices.pump";
        DataSchema schema = DataSchema.builder("numeric").field("value", FieldType.DOUBLE).build();
        Variable speed = new Variable(
                "speed",
                schema,
                true,
                false,
                DataRecord.single(schema, Map.of("value", 42.0))
        );
        Variable secret = new Variable(
                "secretSetpoint",
                schema,
                true,
                false,
                DataRecord.single(schema, Map.of("value", 99.0)),
                false,
                null,
                List.of("engineer"),
                List.of()
        );
        PlatformObject node = new PlatformObject(
                "pump",
                path,
                ObjectType.DEVICE,
                "Pump",
                "",
                null
        );
        node.addVariable(speed);
        node.addVariable(secret);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );
        VariableMemberAccessService variableAccess = mock(VariableMemberAccessService.class);
        when(variableAccess.filterReadable(eq(path), anyCollection(), eq(authentication)))
                .thenReturn(List.of(speed));

        ObjectEditorDto editor = ObjectEditorDto.from(node, null, authentication, variableAccess);

        assertThat(editor.variables()).extracting(VariableDto::name).containsExactly("speed");
        assertThat(editor.object().variableNames()).containsExactly("speed");
        assertThat(editor.toString()).doesNotContain("secretSetpoint", "99.0");
    }
}
