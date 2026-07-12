package com.ispf.server.query.oq;

import com.ispf.core.object.ObjectType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ObjectQuerySpecParser {

    private static final int DEFAULT_MAX_ROWS = 1000;

    private final ObjectMapper objectMapper;

    public ObjectQuerySpecParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectQuerySpec parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("OQ spec JSON is required");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return parse(root);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid OQ spec JSON: " + ex.getMessage(), ex);
        }
    }

    public ObjectQuerySpec parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("OQ spec must be a JSON object");
        }
        ObjectQueryFromSpec from = parseFrom(root.get("from"), root);
        List<ObjectQueryJoinSpec> joins = parseJoins(root.get("joins"));
        List<ObjectQueryFieldSpec> fields = parseFields(root.get("fields"));
        List<ObjectQueryOrderSpec> orderBy = parseOrderBy(root.get("orderBy"));
        Integer limit = root.hasNonNull("limit") ? root.get("limit").asInt() : null;
        Integer offset = root.hasNonNull("offset") ? root.get("offset").asInt() : null;
        String having = textOrNull(root.get("having"));
        List<String> groupBy = parseStringArray(root.get("groupBy"));
        List<ObjectQueryAggregateSpec> aggregates = parseAggregates(root.get("aggregates"));
        if (limit == null) {
            limit = DEFAULT_MAX_ROWS;
        }
        return new ObjectQuerySpec(from, joins, fields, orderBy, limit, offset, having, groupBy, aggregates);
    }

    public String writeSpec(ObjectQuerySpec spec) {
        try {
            return objectMapper.writeValueAsString(toJsonMap(spec));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize OQ spec: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> toJsonMap(ObjectQuerySpec spec) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (spec.from() != null) {
            Map<String, Object> from = new LinkedHashMap<>();
            ObjectQueryFromSpec fromSpec = spec.from();
            if (fromSpec.alias() != null) {
                from.put("alias", fromSpec.alias());
            }
            from.put("sourcePathPattern", fromSpec.sourcePathPattern());
            if (fromSpec.objectTypes() != null && !fromSpec.objectTypes().isEmpty()) {
                from.put("objectTypes", fromSpec.objectTypes());
            }
            if (fromSpec.filter() != null) {
                from.put("filter", fromSpec.filter());
            }
            if (fromSpec.expand() != null) {
                Map<String, Object> expand = new LinkedHashMap<>();
                expand.put("variable", fromSpec.expand().variable());
                if (fromSpec.expand().rowKey() != null) {
                    expand.put("rowKey", fromSpec.expand().rowKey());
                }
                if (fromSpec.expand().filter() != null) {
                    expand.put("filter", fromSpec.expand().filter());
                }
                from.put("expand", expand);
            }
            root.put("from", from);
        }
        if (spec.joins() != null && !spec.joins().isEmpty()) {
            root.put("joins", spec.joins());
        }
        if (spec.fields() != null && !spec.fields().isEmpty()) {
            root.put("fields", spec.fields());
        }
        if (spec.orderBy() != null && !spec.orderBy().isEmpty()) {
            root.put("orderBy", spec.orderBy());
        }
        if (spec.limit() != null) {
            root.put("limit", spec.limit());
        }
        if (spec.offset() != null) {
            root.put("offset", spec.offset());
        }
        if (spec.having() != null) {
            root.put("having", spec.having());
        }
        if (spec.groupBy() != null && !spec.groupBy().isEmpty()) {
            root.put("groupBy", spec.groupBy());
        }
        if (spec.aggregates() != null && !spec.aggregates().isEmpty()) {
            root.put("aggregates", spec.aggregates());
        }
        return root;
    }

    private ObjectQueryFromSpec parseFrom(JsonNode fromNode, JsonNode root) {
        if (fromNode != null && fromNode.isObject()) {
            return new ObjectQueryFromSpec(
                    textOrNull(fromNode.get("alias")),
                    textOrNull(fromNode.get("sourcePathPattern")),
                    parseObjectTypes(fromNode.get("objectTypes")),
                    textOrNull(fromNode.get("filter")),
                    parseExpand(fromNode.get("expand"))
            );
        }
        String pattern = textOrNull(root.get("sourcePathPattern"));
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("from.sourcePathPattern is required");
        }
        return new ObjectQueryFromSpec(
                "row",
                pattern,
                parseObjectTypes(root.get("objectTypes")),
                textOrNull(root.get("filter")),
                null
        );
    }

    private static ObjectQueryExpandSpec parseExpand(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String variable = textOrNull(node.get("variable"));
        if (variable == null || variable.isBlank()) {
            return null;
        }
        return new ObjectQueryExpandSpec(
                variable,
                textOrNull(node.get("rowKey")),
                textOrNull(node.get("filter"))
        );
    }

    private List<ObjectQueryJoinSpec> parseJoins(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ObjectQueryJoinSpec> joins = new ArrayList<>();
        for (JsonNode joinNode : node) {
            if (!joinNode.isObject()) {
                continue;
            }
            joins.add(new ObjectQueryJoinSpec(
                    textOrNull(joinNode.get("alias")),
                    textOrNull(joinNode.get("type")),
                    textOrNull(joinNode.get("sourcePathPattern")),
                    parseObjectTypes(joinNode.get("objectTypes")),
                    textOrNull(joinNode.get("filter")),
                    parseJoinOn(joinNode.get("on"))
            ));
        }
        return joins;
    }

    private static ObjectQueryJoinOnSpec parseJoinOn(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new ObjectQueryJoinOnSpec(JoinKind.PARENT, null, null, null, null);
        }
        String kindRaw = textOrNull(node.get("kind"));
        JoinKind kind = JoinKind.PARENT;
        if (kindRaw != null) {
            kind = JoinKind.valueOf(kindRaw.trim().toUpperCase().replace('-', '_'));
        }
        return new ObjectQueryJoinOnSpec(
                kind,
                textOrNull(node.get("left")),
                textOrNull(node.get("right")),
                textOrNull(node.get("match")),
                textOrNull(node.get("catalogPathPattern"))
        );
    }

    private List<ObjectQueryFieldSpec> parseFields(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ObjectQueryFieldSpec> fields = new ArrayList<>();
        for (JsonNode fieldNode : node) {
            if (!fieldNode.isObject()) {
                continue;
            }
            String name = textOrNull(fieldNode.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            fields.add(new ObjectQueryFieldSpec(
                    name,
                    textOrNull(fieldNode.get("source")),
                    textOrNull(fieldNode.get("alias")),
                    textOrNull(fieldNode.get("ref")),
                    textOrNull(fieldNode.get("expression")),
                    fieldNode.path("writable").asBoolean(false),
                    parseHistorianFn(fieldNode.get("historian")),
                    parseHistorianWindow(fieldNode.get("historian"))
            ));
        }
        return fields;
    }

    private List<ObjectQueryOrderSpec> parseOrderBy(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ObjectQueryOrderSpec> orderBy = new ArrayList<>();
        for (JsonNode orderNode : node) {
            if (!orderNode.isObject()) {
                continue;
            }
            orderBy.add(new ObjectQueryOrderSpec(
                    textOrNull(orderNode.get("field")),
                    textOrNull(orderNode.get("dir"))
            ));
        }
        return orderBy;
    }

    private List<ObjectQueryAggregateSpec> parseAggregates(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ObjectQueryAggregateSpec> aggregates = new ArrayList<>();
        for (JsonNode aggNode : node) {
            if (!aggNode.isObject()) {
                continue;
            }
            aggregates.add(new ObjectQueryAggregateSpec(
                    textOrNull(aggNode.get("name")),
                    textOrNull(aggNode.get("fn")),
                    textOrNull(aggNode.get("ref")),
                    textOrNull(aggNode.get("field"))
            ));
        }
        return aggregates;
    }

    private static Set<String> parseObjectTypes(JsonNode node) {
        Set<String> types = new LinkedHashSet<>();
        if (node != null && node.isArray()) {
            for (JsonNode typeNode : node) {
                String raw = typeNode.asString(null);
                if (raw != null && !raw.isBlank()) {
                    types.add(raw.trim().toUpperCase());
                }
            }
        }
        return types;
    }

    private static List<String> parseStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asString(null);
            if (text != null && !text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private static String parseHistorianFn(JsonNode historianNode) {
        if (historianNode == null || !historianNode.isObject()) {
            return null;
        }
        return textOrNull(historianNode.get("fn"));
    }

    private static String parseHistorianWindow(JsonNode historianNode) {
        if (historianNode == null || !historianNode.isObject()) {
            return null;
        }
        return textOrNull(historianNode.get("window"));
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asString(null);
        return text != null && !text.isBlank() ? text : null;
    }
}
