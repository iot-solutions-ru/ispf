package com.ispf.server.platform;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.driver.DriverPointMappingParser;
import com.ispf.server.driver.DriverPointMappingParser.Entry;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristic Brick class inference from Haystack tags and device metadata (BL-104 / S23-01).
 */
@Service
public class BrickClassInferenceService {

    public static final String CONFIDENCE_HIGH = "high";
    public static final String CONFIDENCE_MEDIUM = "medium";
    public static final String CONFIDENCE_LOW = "low";

    private static final String DEFAULT_POINT_CLASS = BrickExportService.BRICK_NS + "Sensor";

    private record Rule(String brickClass, String confidence, String reason, TagMatcher matcher) {
    }

    @FunctionalInterface
    private interface TagMatcher {
        boolean matches(Set<String> tags, String displayName);
    }

    private static final List<Rule> EQUIP_RULES = List.of(
            rule("Air_Handler_Unit", CONFIDENCE_HIGH, "haystack tag 'ahu'",
                    tags("ahu")),
            rule("Air_Handler_Unit", CONFIDENCE_MEDIUM, "display name matches AHU / air handler",
                    (tags, displayName) -> matchesPattern(displayName, "(?i)(\\bahu\\b|air[- ]?handler)")),
            rule("Meter", CONFIDENCE_HIGH, "haystack tag 'meter'",
                    tags("meter")),
            rule("Meter", CONFIDENCE_MEDIUM, "display name matches meter",
                    (tags, displayName) -> matchesPattern(displayName, "(?i)\\bmeter\\b")),
            rule("Chiller", CONFIDENCE_HIGH, "haystack tag 'chiller'", tags("chiller")),
            rule("Boiler", CONFIDENCE_HIGH, "haystack tag 'boiler'", tags("boiler")),
            rule("Pump", CONFIDENCE_HIGH, "haystack tag 'pump'", tags("pump")),
            rule("Fan", CONFIDENCE_HIGH, "haystack tag 'fan'", tags("fan")),
            rule("VAV", CONFIDENCE_HIGH, "haystack tag 'vav'", tags("vav")),
            rule("Sensor", CONFIDENCE_HIGH, "haystack tags 'temp' and 'sensor'",
                    tagsAll("temp", "sensor")),
            rule("Sensor", CONFIDENCE_MEDIUM, "display name matches temperature sensor",
                    (tags, displayName) -> matchesPattern(displayName, "(?i)temp(erature)?\\s*sensor"))
    );

    private static final List<Rule> POINT_RULES = List.of(
            rule("Temperature_Sensor", CONFIDENCE_HIGH, "haystack tag 'temp'",
                    tags("temp")),
            rule("Temperature_Sensor", CONFIDENCE_MEDIUM, "display name matches temperature",
                    (tags, displayName) -> matchesPattern(displayName, "(?i)temp(erature)?")),
            rule("Humidity_Sensor", CONFIDENCE_HIGH, "haystack tag 'humidity'", tags("humidity")),
            rule("CO2_Sensor", CONFIDENCE_HIGH, "haystack tag 'co2'", tags("co2")),
            rule("Energy_Sensor", CONFIDENCE_HIGH, "haystack power/energy tag",
                    tagsAny("power", "energy", "kw", "kwh", "elec")),
            rule("Flow_Sensor", CONFIDENCE_HIGH, "haystack flow/water tag",
                    tagsAny("flow", "water")),
            rule("Pressure_Sensor", CONFIDENCE_HIGH, "haystack tag 'pressure'", tags("pressure"))
    );

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public BrickClassInferenceService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    public record InferenceResult(
            String brickClass,
            String brickClassCompact,
            String confidence,
            String reason,
            String entityKind
    ) {
        public Map<String, Object> toPayload() {
            return Map.of(
                    "brickClass", brickClass,
                    "brickClassCompact", brickClassCompact,
                    "confidence", confidence,
                    "reason", reason,
                    "entityKind", entityKind
            );
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> inferFromObjectPath(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objectPath is required.");
        }
        String normalizedPath = objectPath.trim();
        PlatformObject node = objectManager.tree().findByPath(normalizedPath)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Object not found: " + normalizedPath
                ));
        if (node.type() != ObjectType.DEVICE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Brick inference applies to device objects only."
            );
        }

        List<String> haystackTags = parseHaystackTags(readString(node, "haystackTags"));
        String haystackKind = readString(node, "haystackKind");
        String displayName = node.displayName();
        List<String> pointTags = aggregatePointMappingTags(node);

        InferenceResult result = infer(haystackTags, haystackKind, displayName, pointTags);
        Map<String, Object> payload = new java.util.LinkedHashMap<>(result.toPayload());
        payload.put("objectPath", normalizedPath);
        payload.put("displayName", displayName);
        payload.put("haystackKind", haystackKind);
        payload.put("tags", haystackTags);
        if (!pointTags.isEmpty()) {
            payload.put("pointMappingTags", pointTags);
        }
        return payload;
    }

    public Map<String, Object> inferFromTags(List<String> tags, String haystackKind, String displayName) {
        List<String> normalizedTags = normalizeTags(tags);
        if (normalizedTags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one tag is required.");
        }
        InferenceResult result = infer(normalizedTags, haystackKind, displayName, List.of());
        Map<String, Object> payload = new java.util.LinkedHashMap<>(result.toPayload());
        payload.put("tags", normalizedTags);
        if (haystackKind != null && !haystackKind.isBlank()) {
            payload.put("haystackKind", haystackKind.trim());
        }
        if (displayName != null && !displayName.isBlank()) {
            payload.put("displayName", displayName.trim());
        }
        return payload;
    }

    static InferenceResult infer(
            List<String> haystackTags,
            String haystackKind,
            String displayName,
            List<String> pointMappingTags
    ) {
        Set<String> haystackTagSet = toTagSet(haystackTags);
        String entityKind = resolveEntityKind(haystackKind, haystackTagSet);
        Set<String> primaryTagSet = "point".equals(entityKind)
                ? union(haystackTagSet, toTagSet(pointMappingTags))
                : haystackTagSet;
        List<Rule> rules = "point".equals(entityKind) ? POINT_RULES : EQUIP_RULES;

        for (Rule rule : rules) {
            if (rule.matcher().matches(primaryTagSet, normalizeDisplayName(displayName))) {
                return toResult(rule.brickClass(), rule.confidence(), rule.reason(), entityKind);
            }
        }

        if ("equip".equals(entityKind) && !pointMappingTags.isEmpty()) {
            Set<String> pointTagSet = toTagSet(pointMappingTags);
            for (Rule rule : POINT_RULES) {
                if (rule.matcher().matches(pointTagSet, normalizeDisplayName(displayName))) {
                    return toResult(
                            rule.brickClass(),
                            CONFIDENCE_MEDIUM,
                            rule.reason() + " (from driver point mapping tags)",
                            entityKind
                    );
                }
            }
        }

        String fallbackClass = "point".equals(entityKind) ? "Sensor" : "Equipment";
        return toResult(
                fallbackClass,
                CONFIDENCE_LOW,
                "no matching heuristic; default " + fallbackClass,
                entityKind
        );
    }

    /** Point-level inference aligned with {@link BrickExportService#resolvePointBrickClass}. */
    static String resolvePointBrickClass(Entry mapping) {
        if (mapping != null && mapping.haystackTags().stream().anyMatch(tag -> "temp".equalsIgnoreCase(tag))) {
            return BrickExportService.BRICK_NS + "Temperature_Sensor";
        }
        return DEFAULT_POINT_CLASS;
    }

    private List<String> aggregatePointMappingTags(PlatformObject node) {
        Map<String, Entry> mappings = DriverPointMappingParser.parse(
                readString(node, "driverPointMappingsJson"),
                objectMapper
        );
        Set<String> tags = new LinkedHashSet<>();
        for (Entry entry : mappings.values()) {
            for (String tag : entry.haystackTags()) {
                if (tag != null && !tag.isBlank()) {
                    tags.add(tag.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(tags);
    }

    private List<String> parseHaystackTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return normalizeTags(tags);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            for (String part : tag.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed.toLowerCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(normalized);
    }

    static String resolveEntityKind(String haystackKind, Set<String> tags) {
        if (haystackKind != null && !haystackKind.isBlank()) {
            String kind = haystackKind.trim().toLowerCase(Locale.ROOT);
            if ("point".equals(kind)) {
                return "point";
            }
            if ("equip".equals(kind)) {
                return "equip";
            }
        }
        if (tags.contains("point") && !tags.contains("equip")) {
            return "point";
        }
        return "equip";
    }

    private static InferenceResult toResult(String className, String confidence, String reason, String entityKind) {
        String uri = BrickExportService.resolveBrickClass(className);
        return new InferenceResult(
                uri,
                BrickExportService.compactType(uri),
                confidence,
                reason,
                entityKind
        );
    }

    private static Rule rule(String className, String confidence, String reason, TagMatcher matcher) {
        return new Rule(className, confidence, reason, matcher);
    }

    private static TagMatcher tags(String... required) {
        return (tagSet, displayName) -> {
            for (String tag : required) {
                if (!tagSet.contains(tag.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
            return true;
        };
    }

    private static TagMatcher tagsAll(String... required) {
        return tags(required);
    }

    private static TagMatcher tagsAny(String... candidates) {
        return (tagSet, displayName) -> {
            for (String tag : candidates) {
                if (tagSet.contains(tag.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        };
    }

    private static boolean matchesPattern(String displayName, String regex) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }
        return Pattern.compile(regex).matcher(displayName).find();
    }

    private static Set<String> toTagSet(List<String> tags) {
        Set<String> set = new LinkedHashSet<>();
        for (String tag : normalizeTags(tags)) {
            set.add(tag);
        }
        return set;
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> merged = new LinkedHashSet<>(left);
        merged.addAll(right);
        return merged;
    }

    private static String normalizeDisplayName(String displayName) {
        return displayName != null ? displayName.trim() : "";
    }

    private static String readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .orElse("");
    }
}
