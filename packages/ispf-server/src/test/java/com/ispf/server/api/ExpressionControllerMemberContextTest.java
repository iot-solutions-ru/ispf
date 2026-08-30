package com.ispf.server.api;

import com.ispf.server.expression.ExpressionEvaluationService;
import com.ispf.server.expression.ExpressionFormalVerificationService;
import com.ispf.server.security.acl.VariableAclRequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpressionControllerMemberContextTest {

    @Mock
    private ExpressionEvaluationService evaluationService;

    @Mock
    private ExpressionFormalVerificationService formalVerificationService;

    @Test
    void interactiveEvaluateRunsAsMember() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );
        var request = new ExpressionController.EvaluateRequest(
                "root.platform.devices.demo",
                "self.temperature.value",
                null,
                List.of(),
                null
        );
        when(evaluationService.evaluate(
                eq(request.objectPath()),
                eq(request.expression()),
                eq(request.targetVariable()),
                eq(request.breakpoints()),
                eq(request.resumeFrom()),
                eq(authentication)
        )).thenAnswer(ignored -> {
            assertThat(VariableAclRequestContext.isMemberEnforced()).isTrue();
            assertThat(VariableAclRequestContext.requireAuthentication()).isSameAs(authentication);
            return new ExpressionEvaluationService.EvaluateResult(
                    true,
                    request.expression(),
                    21.5,
                    "Double",
                    null,
                    List.of(),
                    false,
                    null
            );
        });
        ExpressionController controller = new ExpressionController(
                evaluationService,
                formalVerificationService
        );

        ExpressionController.EvaluateResponse response = controller.evaluate(request, authentication);

        assertThat(response.valid()).isTrue();
        assertThat(response.result()).isEqualTo(21.5);
        assertThat(VariableAclRequestContext.isMemberEnforced()).isFalse();
    }
}
