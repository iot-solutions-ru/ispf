package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.security.acl.VariableAclRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/objects/by-path/functions")
public class FunctionController {

    static final int MAX_BATCH_SIZE = 100;

    private final FunctionService functionService;
    private final FunctionInvokeAccessService invokeAccessService;

    public FunctionController(
            FunctionService functionService,
            FunctionInvokeAccessService invokeAccessService
    ) {
        this.functionService = functionService;
        this.invokeAccessService = invokeAccessService;
    }

    @PostMapping("/invoke")
    public DataRecord invoke(
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody(required = false) DataRecordPayloadRequest input,
            Authentication authentication
    ) {
        invokeAccessService.requireDirectInvoke(path, name, authentication);
        return VariableAclRequestContext.callAsMember(
                authentication,
                () -> functionService.invoke(path, name, input)
        );
    }

    /**
     * Batch function invoke (operator alarm-bar “ack all”, mass workflows).
     * Per-item ACL; one failure does not abort the rest.
     */
    @PostMapping("/invoke-batch")
    public BatchInvokeResponse invokeBatch(
            @RequestBody BatchInvokeRequest request,
            Authentication authentication
    ) {
        List<BatchInvokeItem> items = request != null && request.items() != null
                ? request.items()
                : List.of();
        if (items.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Batch size " + items.size() + " exceeds limit " + MAX_BATCH_SIZE
            );
        }
        List<BatchInvokeResult> results = new ArrayList<>(items.size());
        for (BatchInvokeItem item : items) {
            String path = item != null ? item.path() : null;
            String name = item != null ? item.name() : null;
            if (path == null || path.isBlank() || name == null || name.isBlank()) {
                results.add(BatchInvokeResult.failure(path, name, 400, "path and name are required"));
                continue;
            }
            try {
                invokeAccessService.requireDirectInvoke(path, name, authentication);
                DataRecord result = VariableAclRequestContext.callAsMember(
                        authentication,
                        () -> functionService.invoke(path, name, item.input())
                );
                results.add(BatchInvokeResult.success(path, name, result));
            } catch (ResponseStatusException e) {
                int status = e.getStatusCode() != null ? e.getStatusCode().value() : HttpStatus.FORBIDDEN.value();
                results.add(BatchInvokeResult.failure(path, name, status, e.getReason()));
            } catch (RuntimeException e) {
                results.add(BatchInvokeResult.failure(path, name, 400, e.getMessage()));
            }
        }
        return new BatchInvokeResponse(results);
    }

    public record BatchInvokeRequest(List<BatchInvokeItem> items) {
    }

    public record BatchInvokeItem(String path, String name, DataRecordPayloadRequest input) {
    }

    public record BatchInvokeResponse(List<BatchInvokeResult> results) {
    }

    public record BatchInvokeResult(
            String path,
            String name,
            boolean ok,
            Integer status,
            String error,
            DataRecord result
    ) {
        static BatchInvokeResult success(String path, String name, DataRecord result) {
            return new BatchInvokeResult(path, name, true, null, null, result);
        }

        static BatchInvokeResult failure(String path, String name, int status, String error) {
            return new BatchInvokeResult(path, name, false, status, error, null);
        }
    }
}
