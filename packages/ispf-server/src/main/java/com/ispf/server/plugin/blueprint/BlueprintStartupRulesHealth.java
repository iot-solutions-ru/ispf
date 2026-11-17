package com.ispf.server.plugin.blueprint;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects unresolved startup blueprint-rule problems for readiness.
 * Empty list means healthy (UP).
 */
@Component
public class BlueprintStartupRulesHealth {

    private final AtomicReference<List<String>> problems = new AtomicReference<>(List.of());

    public void reset() {
        problems.set(List.of());
    }

    public void report(String problem) {
        if (problem == null || problem.isBlank()) {
            return;
        }
        problems.updateAndGet(current -> {
            List<String> next = new ArrayList<>(current);
            next.add(problem);
            return List.copyOf(next);
        });
    }

    public void reportAll(List<String> more) {
        if (more == null || more.isEmpty()) {
            return;
        }
        for (String problem : more) {
            report(problem);
        }
    }

    public boolean isReady() {
        return problems.get().isEmpty();
    }

    public List<String> problems() {
        return problems.get();
    }
}
