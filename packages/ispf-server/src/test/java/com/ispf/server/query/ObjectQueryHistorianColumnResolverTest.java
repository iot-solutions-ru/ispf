package com.ispf.server.query;

import com.ispf.core.ref.PlatformRefParser;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.security.acl.VariableAclRequestContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ObjectQueryHistorianColumnResolverTest {

    @Mock
    private ObjectProvider<VariableHistoryService> variableHistoryService;

    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    @Test
    void parseWindowDefaultsToFifteenMinutes() {
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow(null)).isEqualTo(Duration.ofMinutes(15));
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow("")).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void parseWindowAcceptsBucketSpecs() {
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow("1h")).isEqualTo(Duration.ofHours(1));
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow("5m")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void memberHistorianReadChecksAclBeforeQuery() {
        String path = "root.platform.devices.restricted";
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(variableMemberAccessService)
                .requireRead(path, "secret", authentication);
        ObjectQueryHistorianColumnResolver resolver = new ObjectQueryHistorianColumnResolver(
                variableHistoryService,
                variableMemberAccessService
        );

        assertThatThrownBy(() -> VariableAclRequestContext.callAsMember(
                authentication,
                () -> resolver.resolve(
                        "latest",
                        "15m",
                        PlatformRefParser.parse(path + "/secret")
                )
        )).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(variableHistoryService);
    }
}
