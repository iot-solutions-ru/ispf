package com.ispf.server.api;

import com.ispf.server.workflow.WorkflowInstanceCancelService;
import com.ispf.server.workflow.WorkflowService;
import com.ispf.plugin.workflow.WorkflowException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows/instances")
public class WorkflowInstanceController {

    private final WorkflowInstanceCancelService cancelService;
    private final WorkflowService workflowService;

    public WorkflowInstanceController(
            WorkflowInstanceCancelService cancelService,
            WorkflowService workflowService
    ) {
        this.cancelService = cancelService;
        this.workflowService = workflowService;
    }

    @PostMapping("/{instanceId}/cancel")
    public Map<String, Object> cancel(
            @PathVariable String instanceId,
            @RequestBody CancelWorkflowRequest request
    ) {
        return cancelService.cancel(
                instanceId,
                request.reason(),
                request.detailJson(),
                request.cancelledBy()
        );
    }

    @PostMapping("/{instanceId}/signal")
    public Map<String, Object> signal(
            @PathVariable String instanceId,
            @RequestBody SignalWorkflowRequest request
    ) throws WorkflowException {
        return workflowService.deliverSignal(instanceId, request.signal(), request.operatorId());
    }

    @PostMapping("/{instanceId}/timer")
    public Map<String, Object> timer(
            @PathVariable String instanceId,
            @RequestBody TimerWorkflowRequest request
    ) throws WorkflowException {
        return workflowService.fireDueTimers(instanceId, request.operatorId());
    }

    public record CancelWorkflowRequest(String reason, String detailJson, String cancelledBy) {
    }

    public record SignalWorkflowRequest(String signal, String operatorId) {
    }

    public record TimerWorkflowRequest(String operatorId) {
    }
}
