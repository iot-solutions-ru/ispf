package com.ispf.server.application.bundle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BundleValidationResult(
        String status,
        List<String> errors,
        List<String> warnings,
        List<String> wouldApply,
        List<BundleValidationIssue> issues
) {
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    public BundleValidationResult(
            String status,
            List<String> errors,
            List<String> warnings,
            List<String> wouldApply
    ) {
        this(status, errors, warnings, wouldApply, List.of());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status);
        map.put("errors", errors);
        map.put("warnings", warnings);
        if (wouldApply != null && !wouldApply.isEmpty()) {
            map.put("wouldApply", wouldApply);
        }
        if (issues != null && !issues.isEmpty()) {
            map.put("issues", issues.stream().map(BundleValidationIssue::toMap).toList());
        }
        return map;
    }

    public static BundleValidationResult ok(List<String> warnings, List<String> wouldApply) {
        return ok(warnings, wouldApply, List.of());
    }

    public static BundleValidationResult ok(
            List<String> warnings,
            List<String> wouldApply,
            List<BundleValidationIssue> issues
    ) {
        return new BundleValidationResult(
                OK, List.of(), List.copyOf(warnings), List.copyOf(wouldApply), List.copyOf(issues)
        );
    }

    public static BundleValidationResult error(List<String> errors, List<String> warnings, List<String> wouldApply) {
        return error(errors, warnings, wouldApply, List.of());
    }

    public static BundleValidationResult error(
            List<String> errors,
            List<String> warnings,
            List<String> wouldApply,
            List<BundleValidationIssue> issues
    ) {
        return new BundleValidationResult(
                ERROR, List.copyOf(errors), List.copyOf(warnings), List.copyOf(wouldApply), List.copyOf(issues)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> wouldApply = new ArrayList<>();
        private final List<BundleValidationIssue> issues = new ArrayList<>();

        public Builder addError(String message) {
            errors.add(message);
            return this;
        }

        public Builder addWarning(String message) {
            warnings.add(message);
            return this;
        }

        public Builder addIssue(BundleValidationIssue issue) {
            if (issue == null) {
                return this;
            }
            issues.add(issue);
            String text = formatIssue(issue);
            if (BundleValidationIssue.ERROR.equals(issue.severity())) {
                errors.add(text);
            } else {
                warnings.add(text);
            }
            return this;
        }

        public Builder addWouldApply(String section) {
            wouldApply.add(section);
            return this;
        }

        public BundleValidationResult build() {
            if (errors.isEmpty()) {
                return ok(warnings, wouldApply, issues);
            }
            return BundleValidationResult.error(errors, warnings, wouldApply, issues);
        }

        private static String formatIssue(BundleValidationIssue issue) {
            StringBuilder sb = new StringBuilder();
            if (issue.code() != null && !issue.code().isBlank()) {
                sb.append('[').append(issue.code()).append("] ");
            }
            if (issue.path() != null && !issue.path().isBlank()) {
                sb.append(issue.path()).append(": ");
            }
            sb.append(issue.message() != null ? issue.message() : "");
            return sb.toString();
        }
    }
}
