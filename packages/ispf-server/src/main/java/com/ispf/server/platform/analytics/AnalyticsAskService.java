package com.ispf.server.platform.analytics;

import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.workflow.WorkflowAiActionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ask-AI helper for analytics tag inspector (ADR-0049 Wave 2C).
 */
@Service
public class AnalyticsAskService {

    private final AnalyticsAnalysisService analysisService;
    private final VariableHistoryService variableHistoryService;
    private final WorkflowAiActionService workflowAiActionService;

    public AnalyticsAskService(
            AnalyticsAnalysisService analysisService,
            VariableHistoryService variableHistoryService,
            WorkflowAiActionService workflowAiActionService
    ) {
        this.analysisService = analysisService;
        this.variableHistoryService = variableHistoryService;
        this.workflowAiActionService = workflowAiActionService;
    }

    public Map<String, Object> askTrend(String objectPath, String variable, int hours) throws Exception {
        Instant end = Instant.now();
        Instant start = end.minus(Math.max(1, hours), ChronoUnit.HOURS);
        List<Double> series = new ArrayList<>();
        var response = variableHistoryService.query(objectPath, variable, "value", start, end, 500);
        for (var sample : response.samples()) {
            series.add(sample.value() != null ? sample.value() : Double.NaN);
        }
        Map<String, Object> summary = analysisService.analyzeSeries(series, 3.0);
        String narrative = workflowAiActionService.llmComplete(
                "Summarize this OT analytics tag trend in 2-4 short sentences for an operator. Tag="
                        + objectPath + "/" + variable + " Data=" + summary,
                "platform-default",
                30_000
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("objectPath", objectPath);
        result.put("variable", variable);
        result.put("hours", hours);
        result.put("summary", summary);
        result.put("narrative", narrative);
        return result;
    }
}
