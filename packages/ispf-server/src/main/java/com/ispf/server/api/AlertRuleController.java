package com.ispf.server.api;

import com.ispf.server.alert.AlertRule;
import com.ispf.server.alert.AlertRuleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @GetMapping
    public List<AlertRule> list() {
        return alertRuleService.list();
    }

    @GetMapping("/by-path")
    public AlertRule get(@RequestParam String path) {
        return alertRuleService.get(path);
    }

    @PostMapping
    public AlertRule create(@RequestBody AlertRuleService.CreateAlertRuleRequest request) {
        return alertRuleService.create(request);
    }

    @PutMapping("/by-path")
    public AlertRule update(@RequestParam String path, @RequestBody AlertRuleService.UpdateAlertRuleRequest request) {
        return alertRuleService.update(path, request);
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path) {
        alertRuleService.delete(path);
    }
}
