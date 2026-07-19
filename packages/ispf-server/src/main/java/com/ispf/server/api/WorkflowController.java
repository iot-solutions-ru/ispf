package com.ispf.server.api;

import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.workflow.WorkflowService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/by-path")
    public WorkflowService.WorkflowView get(@RequestParam String path) {
        return workflowService.getWorkflow(path);
    }

    @PutMapping("/by-path/bpmn")
    public WorkflowService.WorkflowView saveBpmn(
            @RequestParam String path,
            @RequestBody SaveBpmnRequest request
    ) throws WorkflowException {
        return workflowService.saveBpmn(path, request.bpmnXml());
    }

    @PutMapping("/by-path/status")
    public WorkflowService.WorkflowView updateStatus(
            @RequestParam String path,
            @RequestBody UpdateStatusRequest request
    ) {
        return workflowService.updateStatus(path, request.status());
    }

    @PutMapping("/by-path/operator-app")
    public WorkflowService.WorkflowView updateOperatorApp(
            @RequestParam String path,
            @RequestBody UpdateOperatorAppRequest request
    ) {
        return workflowService.updateOperatorAppId(path, request.operatorAppId());
    }

    @PostMapping("/by-path/run")
    public WorkflowService.WorkflowView run(
            @RequestParam String path,
            @RequestParam(required = false) String triggerObjectPath,
            @RequestBody(required = false) RunWorkflowRequest request
    ) throws WorkflowException {
        Map<String, String> input = request == null || request.input() == null ? Map.of() : request.input();
        return workflowService.runWorkflow(
                path,
                triggerObjectPath,
                com.ispf.server.platform.AutomationMetricsRecorder.WorkflowStartTrigger.MANUAL,
                input
        );
    }

    @PostMapping("/by-path/invoke-tool")
    public Map<String, Object> invokeTool(
            @RequestParam String path,
            @RequestBody(required = false) RunWorkflowRequest request
    ) throws WorkflowException {
        Map<String, String> input = request == null || request.input() == null ? Map.of() : request.input();
        return workflowService.invokeWorkflowTool(path, input);
    }

    @GetMapping("/by-path/runs")
    public List<Map<String, Object>> runs(@RequestParam String path) {
        return workflowService.listRuns(path);
    }

    @GetMapping("/by-path/dead-letters")
    public List<Map<String, Object>> deadLetters(
            @RequestParam String path,
            @RequestParam(defaultValue = "true") boolean unresolvedOnly
    ) {
        return workflowService.listDeadLetters(path, unresolvedOnly);
    }

    @PostMapping("/dead-letters/{id}/resolve")
    public Map<String, Object> resolveDeadLetter(@PathVariable String id) {
        return workflowService.resolveDeadLetter(id);
    }

    @PostMapping("/signal")
    public Map<String, Object> signal(@RequestBody SignalBroadcastRequest request) throws WorkflowException {
        return workflowService.deliverSignalByWorkflowPath(
                request.workflowPath(),
                request.signal(),
                request.operatorId()
        );
    }

    public record SaveBpmnRequest(@NotBlank String bpmnXml) {
    }

    public record UpdateStatusRequest(WorkflowLifecycleStatus status) {
    }

    public record UpdateOperatorAppRequest(String operatorAppId) {
    }

    public record SignalBroadcastRequest(String workflowPath, String signal, String operatorId) {
    }

    public record RunWorkflowRequest(Map<String, String> input) {
    }
}
