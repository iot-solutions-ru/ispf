package com.ispf.core.ref;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses canonical PlatformRef slash grammar (ADR-0043).
 */
public final class PlatformRefParser {

    private static final String IDENT = "[A-Za-z_][A-Za-z0-9_]*";
    private static final String VAR_NAME = "[A-Za-z_][A-Za-z0-9_-]*";
    private static final Pattern IDENT_PATTERN = Pattern.compile("^" + IDENT + "$");

    private static final String NAME = "[A-Za-z_][A-Za-z0-9_-]*";
    private static final String OBJECT_PATH = "[A-Za-z_][A-Za-z0-9_.-]*";

    private static final Pattern SLASH_REF = Pattern.compile(
            "^(@|(?:" + OBJECT_PATH + "))/(fn|evt|tag)/(" + NAME + ")$"
    );

    private static final Pattern SLASH_VAR_REF = Pattern.compile(
            "^(@|(?:" + OBJECT_PATH + "))/(" + VAR_NAME + ")(?:/(" + IDENT + "))?$"
    );

    private PlatformRefParser() {
    }

    public static PlatformRef parse(String raw) {
        return parseOptional(raw).orElseThrow(() -> new PlatformRefParseException("Invalid PlatformRef: " + raw));
    }

    public static Optional<PlatformRef> parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();

        if (trimmed.contains("/")) {
            return parseSlashForm(trimmed);
        }

        if (IDENT_PATTERN.matcher(trimmed).matches()) {
            return Optional.of(PlatformRef.currentVariable(trimmed));
        }

        return Optional.empty();
    }

    public static PlatformRef parseVariableSource(String raw) {
        Optional<PlatformRef> parsed = parseOptional(raw);
        if (parsed.isEmpty()) {
            throw new PlatformRefParseException("Invalid variable source: " + raw);
        }
        PlatformRef ref = parsed.get();
        if (!ref.isVariable()) {
            throw new PlatformRefParseException("Expected variable ref: " + raw);
        }
        return ref;
    }

    public static Set<PlatformRef> extractRefsFromExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return Set.of();
        }
        Set<PlatformRef> refs = new LinkedHashSet<>();
        extractSlashRefs(expression, refs);
        return refs;
    }

    public static Optional<PlatformRef> fromJsonFields(
            String ref,
            String objectPath,
            String name,
            String field,
            PlatformRefKind kind
    ) {
        if (ref != null && !ref.isBlank()) {
            return parseOptional(ref);
        }
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String object = normalizeObjectPath(objectPath);
        return switch (kind) {
            case VARIABLE -> Optional.of(PlatformRef.variable(object, name, field != null ? field : PlatformRef.DEFAULT_FIELD));
            case FUNCTION -> Optional.of(PlatformRef.function(object, name));
            case EVENT -> Optional.of(PlatformRef.event(object, name));
            case TAG -> Optional.of(PlatformRef.tag(object, name));
        };
    }

    public static String normalizeObjectPath(String objectPath) {
        if (objectPath == null || objectPath.isBlank() || "self".equals(objectPath)) {
            return PlatformRef.CURRENT_OBJECT;
        }
        return objectPath.trim();
    }

    private static Optional<PlatformRef> parseSlashForm(String trimmed) {
        Matcher kindMatcher = SLASH_REF.matcher(trimmed);
        if (kindMatcher.matches()) {
            PlatformRefKind kind = PlatformRefKind.fromSegment(kindMatcher.group(2));
            return Optional.of(new PlatformRef(kindMatcher.group(1), kind, kindMatcher.group(3), null));
        }
        Matcher varMatcher = SLASH_VAR_REF.matcher(trimmed);
        if (varMatcher.matches()) {
            String field = varMatcher.group(3) != null ? varMatcher.group(3) : PlatformRef.DEFAULT_FIELD;
            return Optional.of(PlatformRef.variable(varMatcher.group(1), varMatcher.group(2), field));
        }
        return Optional.empty();
    }

    private static final Pattern HISTORIAN_VAR = Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]*$");

    /**
     * Parses historian helper source arguments: slash refs and legacy {@code object.path.variable} form.
     */
    public static PlatformRef parseHistorianSource(String raw, String ruleObjectPath) {
        String trimmed = unquote(raw).trim();
        if (trimmed.isBlank()) {
            throw new PlatformRefParseException("Historian source is required");
        }
        Optional<PlatformRef> parsed = parseOptional(trimmed).filter(PlatformRef::isVariable);
        if (parsed.isEmpty() && trimmed.contains(".")) {
            int lastDot = trimmed.lastIndexOf('.');
            if (lastDot > 0 && lastDot < trimmed.length() - 1) {
                String object = trimmed.substring(0, lastDot);
                String name = trimmed.substring(lastDot + 1);
                if (HISTORIAN_VAR.matcher(name).matches()) {
                    parsed = Optional.of(PlatformRef.variable(object, name));
                }
            }
        }
        if (parsed.isEmpty()) {
            throw new PlatformRefParseException("Historian source must be slash ref object/variable: " + raw);
        }
        PlatformRef ref = parsed.get();
        if (ruleObjectPath != null && !ruleObjectPath.isBlank()) {
            ref = ref.resolveObject(ruleObjectPath);
        }
        return ref;
    }

    private static String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void extractSlashRefs(String expression, Set<PlatformRef> refs) {
        for (String token : expression.split("[^@/A-Za-z0-9_./-]+")) {
            if (!token.contains("/")) {
                continue;
            }
            parseOptional(token).ifPresent(refs::add);
        }
    }

    public static Optional<PlatformRef> parseDotFormWithTree(String dotForm, Predicate<String> objectPathExists) {
        if (dotForm == null || !dotForm.contains(".")) {
            return Optional.empty();
        }
        String[] parts = dotForm.split("\\.");
        for (int i = parts.length - 1; i >= 1; i--) {
            String object = String.join(".", java.util.Arrays.copyOf(parts, i));
            String variable = parts[i];
            if (objectPathExists.test(object) && IDENT_PATTERN.matcher(variable).matches()) {
                return Optional.of(PlatformRef.variable(object, variable));
            }
        }
        return Optional.empty();
    }
}
