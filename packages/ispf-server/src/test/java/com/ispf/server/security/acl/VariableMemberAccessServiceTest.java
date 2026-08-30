package com.ispf.server.security.acl;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariableMemberAccessServiceTest {

    private static final String PATH = "root.platform.devices.pump1";
    private static final DataSchema SCHEMA = DataSchema.builder("numeric")
            .field("value", FieldType.DOUBLE)
            .build();
    private static final Authentication AUTHENTICATION =
            UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectAccessService objectAccessService;

    @Mock
    private PlatformObject platformObject;

    private VariableMemberAccessService service;

    @BeforeEach
    void setUp() {
        service = new VariableMemberAccessService(objectManager, objectAccessService);
    }

    @Test
    void requireReadPropagatesAclDenial() {
        Variable variable = variable("pressure", List.of("operator"));
        when(objectManager.require(PATH)).thenReturn(platformObject);
        when(platformObject.getVariable("pressure")).thenReturn(Optional.of(variable));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(objectAccessService)
                .requireVariableRead(PATH, "pressure", List.of("operator"), AUTHENTICATION);

        assertThatThrownBy(() -> service.requireRead(PATH, "pressure", AUTHENTICATION))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void canReadReturnsFalseWhenVariableIsMissing() {
        when(objectManager.require(PATH)).thenReturn(platformObject);
        when(platformObject.getVariable("missing")).thenReturn(Optional.empty());

        assertThat(service.canRead(PATH, "missing", AUTHENTICATION)).isFalse();
        verifyNoInteractions(objectAccessService);
    }

    @Test
    void canReadReturnsFalseWhenObjectIsMissing() {
        when(objectManager.require(PATH)).thenThrow(new ObjectNotFoundException(PATH));

        assertThat(service.canRead(PATH, "pressure", AUTHENTICATION)).isFalse();
        verifyNoInteractions(objectAccessService);
    }

    @Test
    void filterReadableRetainsOnlyVariablesAllowedByAcl() {
        Variable pressure = variable("pressure", List.of("operator"));
        Variable setpoint = variable("setpoint", List.of("engineer"));
        when(objectAccessService.canVariableRead(
                PATH, "pressure", List.of("operator"), AUTHENTICATION
        )).thenReturn(true);
        when(objectAccessService.canVariableRead(
                PATH, "setpoint", List.of("engineer"), AUTHENTICATION
        )).thenReturn(false);

        assertThat(service.filterReadable(PATH, List.of(pressure, setpoint), AUTHENTICATION))
                .containsExactly(pressure);
        verify(objectAccessService).canVariableRead(
                PATH, "pressure", List.of("operator"), AUTHENTICATION
        );
        verify(objectAccessService).canVariableRead(
                PATH, "setpoint", List.of("engineer"), AUTHENTICATION
        );
    }

    private static Variable variable(String name, List<String> readRoles) {
        return new Variable(
                name,
                SCHEMA,
                true,
                true,
                null,
                false,
                null,
                readRoles,
                List.of()
        );
    }
}
