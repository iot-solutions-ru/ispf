package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.server.api.dto.DataRecordPayloadRequest;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/objects/by-path/functions")
public class FunctionController {

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
        return functionService.invoke(path, name, input);
    }
}
