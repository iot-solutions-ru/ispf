package com.ispf.server.api;

import com.ispf.core.binding.BindingRule;
import com.ispf.expression.BindingExpressionValidator;
import com.ispf.expression.ExpressionException;
import com.ispf.server.api.support.ObjectCollaborationSupport;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.acl.ObjectAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/objects")
public class BindingRulesController {

    private final ObjectManager objectManager;
    private final ObjectAccessService objectAccessService;
    private final ObjectEditLeaseService editLeaseService;
    private final BindingRulesService bindingRulesService;
    private final BindingDependencyIndex dependencyIndex;
    private final BindingRuleEngine bindingRuleEngine;

    public BindingRulesController(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            ObjectEditLeaseService editLeaseService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex dependencyIndex,
            BindingRuleEngine bindingRuleEngine
    ) {
        this.objectManager = objectManager;
        this.objectAccessService = objectAccessService;
        this.editLeaseService = editLeaseService;
        this.bindingRulesService = bindingRulesService;
        this.dependencyIndex = dependencyIndex;
        this.bindingRuleEngine = bindingRuleEngine;
    }

    @GetMapping("/by-path/binding-rules")
    public List<BindingRule> listRules(@RequestParam String path, Authentication authentication) {
        objectAccessService.requireRead(path, authentication);
        objectManager.require(path);
        return bindingRulesService.listRules(path);
    }

    @PutMapping("/by-path/binding-rules")
    public List<BindingRule> saveRules(
            @RequestParam String path,
            @Valid @RequestBody List<BindingRule> rules,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            for (BindingRule rule : rules) {
                if (!rule.isHistorian()) {
                    BindingExpressionValidator.validateOrThrow(rule.expression());
                }
                if (rule.condition() != null && !rule.condition().isBlank()) {
                    BindingExpressionValidator.validateOrThrow(rule.condition());
                }
            }
            List<BindingRule> saved = bindingRulesService.saveRules(path, rules);
            dependencyIndex.rebuild(path);
            bindingRuleEngine.runRulesForObject(path);
            return saved;
        } catch (IllegalArgumentException | IllegalStateException | ExpressionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @DeleteMapping("/by-path/binding-rules/{ruleId}")
    public List<BindingRule> deleteRule(
            @RequestParam String path,
            @PathVariable String ruleId,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        beginWrite(path, authentication, headers);
        try {
            bindingRulesService.deleteRule(path, ruleId);
            dependencyIndex.rebuild(path);
            return bindingRulesService.listRules(path);
        } finally {
            endWrite();
        }
    }

    private void beginWrite(String path, Authentication authentication, HttpHeaders headers) {
        objectAccessService.requireWrite(path, authentication);
        editLeaseService.assertWritable(path, authentication != null ? authentication.getName() : "system");
        ObjectCollaborationSupport.bindWriteContext(authentication, headers);
    }

    private void endWrite() {
        ObjectCollaborationSupport.clearContext();
    }
}
