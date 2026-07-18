package com.ispf.server.api;

import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.platform.AutomationMetricsRecorder;
import com.ispf.server.workflow.WorkflowService;
import com.ispf.server.workflow.WorkflowWebhookIndex;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Inbound webhook trigger for ACTIVE workflows (ADR-0049 Wave 3).
 */
@RestController
@RequestMapping("/api/v1/webhooks/workflows")
public class WorkflowWebhookController {

    private final WorkflowService workflowService;
    private final WorkflowWebhookIndex webhookIndex;

    public WorkflowWebhookController(WorkflowService workflowService, WorkflowWebhookIndex webhookIndex) {
        this.workflowService = workflowService;
        this.webhookIndex = webhookIndex;
    }

    @PostMapping("/{slug}")
    public Map<String, Object> trigger(
            @PathVariable String slug,
            @RequestBody(required = false) Map<String, Object> body
    ) throws WorkflowException {
        String path = webhookIndex.resolve(slug)
                .orElseThrow(() -> new IllegalArgumentException("No ACTIVE workflow with webhookSlug=" + slug));
        WorkflowService.WorkflowView view = workflowService.getWorkflow(path);
        if (view.status() != WorkflowLifecycleStatus.ACTIVE) {
            throw new IllegalStateException("Workflow is not ACTIVE: " + path);
        }
        Map<String, String> input = new HashMap<>();
        if (body != null) {
            Object rawInput = body.get("input");
            if (rawInput instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k != null) {
                        input.put(k.toString(), v == null ? "" : v.toString());
                    }
                });
            } else {
                body.forEach((k, v) -> {
                    if (k != null && !"input".equals(k)) {
                        input.put(k, v == null ? "" : v.toString());
                    }
                });
            }
        }
        input.putIfAbsent("webhookSlug", slug);
        WorkflowService.WorkflowView result = workflowService.runWorkflow(
                path,
                null,
                AutomationMetricsRecorder.WorkflowStartTrigger.EVENT,
                input
        );
        return Map.of(
                "status", "OK",
                "workflowPath", path,
                "instanceState", result.instanceState() == null ? "{}" : result.instanceState()
        );
    }
}
