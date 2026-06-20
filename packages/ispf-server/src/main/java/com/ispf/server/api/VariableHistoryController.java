package com.ispf.server.api;

import com.ispf.server.history.VariableHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/objects/by-path/variables")
public class VariableHistoryController {

    private final VariableHistoryService variableHistoryService;

    public VariableHistoryController(VariableHistoryService variableHistoryService) {
        this.variableHistoryService = variableHistoryService;
    }

    @GetMapping("/history")
    public VariableHistoryService.VariableHistoryResponse history(
            @RequestParam String path,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "value") String field,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "500") int limit
    ) {
        try {
            return variableHistoryService.query(path, name, field, from, to, limit);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
