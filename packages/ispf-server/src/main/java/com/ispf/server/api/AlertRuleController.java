package com.ispf.server.api;

import com.ispf.server.alert.AlertRule;
import com.ispf.server.alert.AlertRuleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/{id}")
    public AlertRule get(@PathVariable String id) {
        return alertRuleService.get(id);
    }

    @PostMapping
    public AlertRule create(@RequestBody AlertRuleService.CreateAlertRuleRequest request) {
        return alertRuleService.create(request);
    }

    @PutMapping("/{id}")
    public AlertRule update(@PathVariable String id, @RequestBody AlertRuleService.UpdateAlertRuleRequest request) {
        return alertRuleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        alertRuleService.delete(id);
    }
}
