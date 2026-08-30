package com.ispf.server.platform.analytics;

import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsQueryServiceTest {

    private static final Authentication OPERATOR = UsernamePasswordAuthenticationToken.authenticated(
            "operator",
            "n/a",
            List.of(new SimpleGrantedAuthority("operator"))
    );

    @Mock
    private VariableHistoryService variableHistoryService;

    @Mock
    private com.ispf.server.platform.analytics.frames.EventFrameService eventFrameService;

    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    private final AnalyticsProperties analyticsProperties = new AnalyticsProperties(
            60_000L,
            true,
            true,
            60_000L,
            false,
            60_000L,
            7,
            20,
            3_000L,
            0
    );

    @Test
    void alignsSeriesOnSharedTimestamps() {
        AnalyticsQueryRateLimiter rateLimiter = new AnalyticsQueryRateLimiter(analyticsProperties);
        AnalyticsQueryService service = new AnalyticsQueryService(
                variableHistoryService,
                analyticsProperties,
                rateLimiter,
                eventFrameService,
                variableMemberAccessService
        );
        Instant t1 = Instant.parse("2026-07-09T08:00:00Z");
        Instant t2 = Instant.parse("2026-07-09T09:00:00Z");
        when(variableHistoryService.aggregate(anyString(), anyString(), anyString(), any(), any(), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(0);
                    if ("root.b".equals(path)) {
                        return new VariableHistoryService.VariableHistoryAggregateResponse(
                                path,
                                "temperature",
                                "value",
                                "1h",
                                List.of(
                                        new VariableHistoryService.VariableHistoryBucket(t2, 5.0, 5.0, 5.0, 1)
                                ),
                                "raw"
                        );
                    }
                    return new VariableHistoryService.VariableHistoryAggregateResponse(
                            path,
                            "temperature",
                            "value",
                            "1h",
                            List.of(
                                    new VariableHistoryService.VariableHistoryBucket(t1, 1.0, 1.0, 1.0, 1),
                                    new VariableHistoryService.VariableHistoryBucket(t2, 2.0, 2.0, 2.0, 1)
                            ),
                            "raw"
                    );
                });

        Instant to = Instant.parse("2026-07-09T10:00:00Z");
        Instant from = to.minus(6, ChronoUnit.HOURS);
        AnalyticsQueryResponse response = service.query(new AnalyticsQueryRequest(
                List.of(
                        new AnalyticsQueryRequest.AnalyticsQueryTag("root.a", "temperature", "value", "a"),
                        new AnalyticsQueryRequest.AnalyticsQueryTag("root.b", "temperature", "value", "b")
                ),
                from,
                to,
                "1h",
                "avg",
                100,
                null
        ), OPERATOR);

        assertThat(response.timestamps()).containsExactly(t1.toString(), t2.toString());
        assertThat(response.series().get(0).values()).containsExactly(1.0, 2.0);
        assertThat(response.series().get(1).values()).containsExactly(null, 5.0);
        verify(variableMemberAccessService).requireRead("root.a", "temperature", OPERATOR);
        verify(variableMemberAccessService).requireRead("root.b", "temperature", OPERATOR);
    }

    @Test
    void deniesOperatorWithoutVariableReadRoleBeforeSubmittingQueries() {
        AnalyticsQueryRateLimiter rateLimiter = new AnalyticsQueryRateLimiter(analyticsProperties);
        AnalyticsQueryService service = new AnalyticsQueryService(
                variableHistoryService,
                analyticsProperties,
                rateLimiter,
                eventFrameService,
                variableMemberAccessService
        );
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "variable read denied"))
                .when(variableMemberAccessService)
                .requireRead("root.a", "temperature", OPERATOR);

        Instant to = Instant.parse("2026-07-09T10:00:00Z");
        AnalyticsQueryRequest request = new AnalyticsQueryRequest(
                List.of(new AnalyticsQueryRequest.AnalyticsQueryTag(
                        "root.a",
                        "temperature",
                        "value",
                        "a"
                )),
                to.minus(6, ChronoUnit.HOURS),
                to,
                "1h",
                "avg",
                100,
                null
        );

        assertThatThrownBy(() -> service.query(request, OPERATOR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(variableHistoryService);
    }
}
