package com.ispf.server.plugin.blueprint;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Startup findings for templated blueprint rules whose saved instance rule is missing or still
 * contains {@code ${...}}. Empty means healthy (UP).
 *
 * <p>Every read re-checks each finding against the live object and drops it once remediated at
 * runtime: the saved rule is resolved, the blueprint is detached, or the object is deleted.
 */
@Component
public class BlueprintStartupRulesHealth {

    public enum Kind {
        MISSING,
        UNRESOLVED
    }

    /** One templated blueprint rule on one object. */
    public record Problem(String objectPath, String ruleId, String blueprintId, String blueprintName, Kind kind) {

        public static Problem of(String objectPath, BlueprintDefinition model, String ruleId, Kind kind) {
            return new Problem(objectPath, ruleId, model.id(), model.name(), kind);
        }

        public String message() {
            String detail = switch (kind) {
                case MISSING -> "templated blueprint rule has no saved instance rule";
                case UNRESOLVED -> "saved instance rule still contains ${...}";
            };
            return key() + ": " + detail;
        }

        String key() {
            return objectPath + "#" + ruleId;
        }

        boolean carriedBy(PlatformObject node) {
            Set<String> refs = new HashSet<>(node.appliedBlueprintIds());
            node.templateId().ifPresent(refs::add);
            return refs.contains(blueprintId) || refs.contains(blueprintName);
        }

        Problem withKind(Kind next) {
            return next == kind ? this : new Problem(objectPath, ruleId, blueprintId, blueprintName, next);
        }
    }

    private final ObjectManager objectManager;
    private final BindingRulesService bindingRulesService;
    private final Map<String, Problem> problems = new ConcurrentHashMap<>();

    public BlueprintStartupRulesHealth(ObjectManager objectManager, BindingRulesService bindingRulesService) {
        this.objectManager = objectManager;
        this.bindingRulesService = bindingRulesService;
    }

    public void reset() {
        problems.clear();
    }

    public void reportAll(List<Problem> found) {
        if (found == null) {
            return;
        }
        for (Problem problem : found) {
            problems.put(problem.key(), problem);
        }
    }

    public boolean isReady() {
        return problems().isEmpty();
    }

    /** Findings still present on the live tree; remediated ones are dropped for good. */
    public List<String> problems() {
        List<Problem> current = new ArrayList<>();
        // Re-check outside any lock: startup reports while ObjectManager holds the tree write lock.
        for (Problem problem : List.copyOf(problems.values())) {
            Optional<Problem> still = recheck(problem);
            if (still.isEmpty()) {
                problems.remove(problem.key(), problem);
            } else {
                problems.replace(problem.key(), problem, still.get());
                current.add(still.get());
            }
        }
        return current.stream()
                .sorted(Comparator.comparing(Problem::key))
                .map(Problem::message)
                .toList();
    }

    private Optional<Problem> recheck(Problem problem) {
        try {
            Optional<PlatformObject> node = objectManager.tree().findByPath(problem.objectPath());
            if (node.isEmpty() || !problem.carriedBy(node.get())) {
                return Optional.empty();
            }
            Optional<BindingRule> saved = bindingRulesService.listRules(problem.objectPath()).stream()
                    .filter(rule -> problem.ruleId().equals(rule.id()))
                    .findFirst();
            if (saved.isEmpty()) {
                return Optional.of(problem.withKind(Kind.MISSING));
            }
            if (UnresolvedBindingPlaceholders.present(saved.get())) {
                return Optional.of(problem.withKind(Kind.UNRESOLVED));
            }
            return Optional.empty();
        } catch (ObjectNotFoundException ex) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            // A failed read must not report UP.
            return Optional.of(problem);
        }
    }
}
