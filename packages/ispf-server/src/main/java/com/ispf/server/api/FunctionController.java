package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.server.function.FunctionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/objects/by-path/functions")
public class FunctionController {

    private final FunctionService functionService;

    public FunctionController(FunctionService functionService) {
        this.functionService = functionService;
    }

    @PostMapping("/invoke")
    public DataRecord invoke(
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody(required = false) DataRecord input
    ) {
        return functionService.invoke(path, name, input);
    }
}
