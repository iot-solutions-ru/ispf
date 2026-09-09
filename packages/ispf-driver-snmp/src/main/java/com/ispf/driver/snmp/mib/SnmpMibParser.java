package com.ispf.driver.snmp.mib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight SMIv2 subset parser: OBJECT-TYPE + OBJECT IDENTIFIER + IMPORTS module names.
 * Not a full ASN.1 compiler — enough to build a selectable OID catalog from typical MIBs.
 */
public final class SnmpMibParser {

    private static final Pattern MODULE_HEADER = Pattern.compile(
            "(?m)^\\s*([A-Za-z][A-Za-z0-9-]*)\\s+DEFINITIONS\\s*::=\\s*BEGIN"
    );
    private static final Pattern IMPORTS_BLOCK = Pattern.compile(
            "(?is)IMPORTS\\s+(.*?)\\s*;"
    );
    private static final Pattern FROM_MODULE = Pattern.compile(
            "(?i)\\bFROM\\s+([A-Za-z][A-Za-z0-9-]*)"
    );
    private static final Pattern OBJECT_IDENTIFIER = Pattern.compile(
            "(?ms)([A-Za-z][A-Za-z0-9-]*)\\s+OBJECT\\s+IDENTIFIER\\s*::=\\s*\\{([^}]+)\\}"
    );
    private static final Pattern OBJECT_TYPE = Pattern.compile(
            "(?ms)([A-Za-z][A-Za-z0-9-]*)\\s+OBJECT-TYPE\\s+(.*?)::=\\s*\\{([^}]+)\\}"
    );
    private static final Pattern SYNTAX = Pattern.compile(
            "(?is)\\bSYNTAX\\s+([^\\n]+?)(?=\\n\\s*[A-Z-]|$)"
    );
    private static final Pattern MAX_ACCESS = Pattern.compile(
            "(?is)\\b(?:MAX-ACCESS|ACCESS)\\s+([A-Za-z-]+)"
    );
    private static final Pattern UNITS = Pattern.compile(
            "(?is)\\bUNITS\\s+\"([^\"]*)\""
    );
    private static final Pattern DESCRIPTION = Pattern.compile(
            "(?is)\\bDESCRIPTION\\s+\"((?:\\\\.|[^\"\\\\])*)\""
    );
    private static final Pattern INDEX = Pattern.compile(
            "(?is)\\bINDEX\\s*\\{"
    );
    private static final Pattern AUGMENTS = Pattern.compile(
            "(?is)\\bAUGMENTS\\s*\\{"
    );

    private SnmpMibParser() {
    }

    public static SnmpMibModule parse(String fileName, String source) {
        List<String> errors = new ArrayList<>();
        String text = stripComments(source == null ? "" : source);
        String moduleName = fileName;
        Matcher moduleMatcher = MODULE_HEADER.matcher(text);
        if (moduleMatcher.find()) {
            moduleName = moduleMatcher.group(1);
        } else {
            errors.add("MODULE DEFINITIONS header not found; using file name");
        }

        List<String> imports = new ArrayList<>();
        Matcher importsMatcher = IMPORTS_BLOCK.matcher(text);
        if (importsMatcher.find()) {
            Matcher from = FROM_MODULE.matcher(importsMatcher.group(1));
            Set<String> seen = new LinkedHashSet<>();
            while (from.find()) {
                seen.add(from.group(1));
            }
            imports.addAll(seen);
        }

        Map<String, String> oidAssignments = new LinkedHashMap<>();
        Matcher oidMatcher = OBJECT_IDENTIFIER.matcher(text);
        while (oidMatcher.find()) {
            String name = oidMatcher.group(1);
            String expr = normalizeOidBody(oidMatcher.group(2));
            oidAssignments.put(name, expr);
        }

        Map<String, SnmpMibObject> objects = new LinkedHashMap<>();
        Matcher typeMatcher = OBJECT_TYPE.matcher(text);
        while (typeMatcher.find()) {
            String name = typeMatcher.group(1);
            String body = typeMatcher.group(2);
            String expr = normalizeOidBody(typeMatcher.group(3));
            String syntax = firstGroup(SYNTAX, body).trim();
            String maxAccess = firstGroup(MAX_ACCESS, body).trim();
            String units = firstGroup(UNITS, body);
            String description = unescapeDescription(firstGroup(DESCRIPTION, body));
            SnmpMibObject.Kind kind = classify(syntax, body, name, expr);
            SnmpMibObject object = new SnmpMibObject(
                    moduleName,
                    name,
                    expr,
                    syntax,
                    maxAccess,
                    units,
                    description,
                    kind
            );
            objects.put(name, object);
            oidAssignments.put(name, expr);
        }

        return new SnmpMibModule(fileName, moduleName, oidAssignments, objects, imports, errors);
    }

    private static SnmpMibObject.Kind classify(String syntax, String body, String name, String oidExpr) {
        String syn = syntax.toLowerCase(Locale.ROOT);
        if (syn.contains("sequence")) {
            return SnmpMibObject.Kind.TABLE;
        }
        if (INDEX.matcher(body).find() || AUGMENTS.matcher(body).find()) {
            // Conceptual row (…Entry) — not a selectable leaf
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (lowerName.endsWith("entry")) {
                return SnmpMibObject.Kind.OID_NODE;
            }
            return SnmpMibObject.Kind.COLUMN;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("table")) {
            return SnmpMibObject.Kind.TABLE;
        }
        if (lower.endsWith("entry")) {
            return SnmpMibObject.Kind.OID_NODE;
        }
        // Column convention: OBJECT-TYPE assigned under an …Entry parent, e.g. { ifEntry 10 }
        String parent = oidParentName(oidExpr);
        if (parent != null && parent.toLowerCase(Locale.ROOT).endsWith("entry")) {
            return SnmpMibObject.Kind.COLUMN;
        }
        // Legacy heuristic: some MIBs mention the entry in the DESCRIPTION/body
        String bodyLower = body.toLowerCase(Locale.ROOT);
        if (bodyLower.contains("ifentry") || bodyLower.contains("entry ")) {
            return SnmpMibObject.Kind.COLUMN;
        }
        return SnmpMibObject.Kind.SCALAR;
    }

    /** First token of `{ parent n }` when parent is a name (not a pure numeric OID). */
    private static String oidParentName(String oidExpr) {
        if (oidExpr == null || oidExpr.isBlank()) {
            return null;
        }
        String cleaned = oidExpr.trim();
        if (cleaned.matches("[0-9]+(\\.[0-9]+)+")) {
            return null;
        }
        String[] parts = cleaned.split("[.\\s]+");
        if (parts.length == 0) {
            return null;
        }
        String first = parts[0].trim();
        if (first.isEmpty() || first.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return first;
    }

    private static String normalizeOidBody(String body) {
        String cleaned = body.replace(',', ' ').replaceAll("\\s+", " ").trim();
        // Absolute numeric: 1 3 6 1 ...
        if (cleaned.matches("[0-9]+(\\s+[0-9]+)+")) {
            return cleaned.replace(' ', '.');
        }
        if (cleaned.matches("[0-9]+(\\.[0-9]+)+")) {
            return cleaned;
        }
        return cleaned;
    }

    private static String firstGroup(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1).trim() : "";
        }
        return "";
    }

    private static String unescapeDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replace("\\\"", "\"").replace("\\n", "\n").trim();
    }

    static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inString = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
                inString = !inString;
                out.append(c);
                continue;
            }
            if (!inString && c == '-' && i + 1 < source.length() && source.charAt(i + 1) == '-') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                if (i < source.length()) {
                    out.append('\n');
                }
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
