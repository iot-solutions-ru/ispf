package com.ispf.server.query;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;
import com.ispf.expression.ExpressionEngine;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.query.oq.ObjectQueryAggregateSpec;
import com.ispf.server.query.oq.ObjectQueryExpandSpec;
import com.ispf.server.query.oq.ObjectQueryFieldSpec;
import com.ispf.server.query.oq.ObjectQueryFromSpec;
import com.ispf.server.query.oq.ObjectQueryJoinSpec;
import com.ispf.server.query.oq.ObjectQueryOrderSpec;
import com.ispf.server.query.oq.ObjectQueryRefTemplate;
import com.ispf.server.query.oq.ObjectQueryResult;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.ref.PlatformRefExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ObjectQueryService {

    private final ObjectManager objectManager;
    private final PlatformRefExecutor platformRefExecutor;
    private final com.ispf.server.query.oq.ObjectQueryJoinResolver joinResolver;
    private final ObjectQueryHistorianColumnResolver historianColumnResolver;
    private final ExpressionEngine expressionEngine = new ExpressionEngine();

    public ObjectQueryService(
            ObjectManager objectManager,
            PlatformRefExecutor platformRefExecutor,
            com.ispf.server.query.oq.ObjectQueryJoinResolver joinResolver,
            ObjectQueryHistorianColumnResolver historianColumnResolver
    ) {
        this.objectManager = objectManager;
        this.platformRefExecutor = platformRefExecutor;
        this.joinResolver = joinResolver;
        this.historianColumnResolver = historianColumnResolver;
    }

    public ObjectQueryResult execute(ObjectQuerySpec spec, String ruleObjectPath) {
        if (spec == null || spec.from() == null) {
            throw new IllegalArgumentException("OQ spec requires from");
        }
        ObjectQueryFromSpec from = spec.from();
        String pattern = from.sourcePathPattern();
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("from.sourcePathPattern is required");
        }
        String drivingAlias = from.aliasOrDefault();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (!ObjectPathPattern.matches(node.path(), pattern)) {
                continue;
            }
            if (!matchesObjectTypes(node, from.objectTypes())) {
                continue;
            }
            if (!passesFilter(from.filter(), node)) {
                continue;
            }
            if (from.expand() != null) {
                rows.addAll(expandRows(node, from.expand(), drivingAlias, spec, ruleObjectPath));
            } else {
                rows.add(buildProjectedRow(node, null, drivingAlias, spec, ruleObjectPath));
            }
        }
        rows = applyHaving(rows, spec.having());
        rows = applyGroupBy(rows, spec);
        rows = sortRows(rows, spec.orderBy());
        rows = paginate(rows, spec.limit(), spec.offset());
        return new ObjectQueryResult(List.copyOf(rows), rows.size());
    }

    public Object executeAggregate(ObjectQuerySpec spec, String aggregate, String field, String ruleObjectPath) {
        ObjectQueryResult result = execute(spec, ruleObjectPath);
        String fn = aggregate != null ? aggregate.trim().toLowerCase() : "count";
        return switch (fn) {
            case "count" -> result.rowCount();
            case "sum" -> sumField(result.rows(), requireField(field));
            case "avg" -> avgField(result.rows(), requireField(field));
            case "min" -> minField(result.rows(), requireField(field));
            case "max" -> maxField(result.rows(), requireField(field));
            case "first" -> firstField(result.rows(), requireField(field));
            default -> throw new IllegalArgumentException("Unsupported OQ aggregate: " + aggregate);
        };
    }

    private List<Map<String, Object>> expandRows(
            PlatformObject node,
            ObjectQueryExpandSpec expand,
            String drivingAlias,
            ObjectQuerySpec spec,
            String ruleObjectPath
    ) {
        List<Map<String, Object>> expanded = new ArrayList<>();
        Optional<Variable> variable = node.getVariable(expand.variable());
        if (variable.isEmpty()) {
            return expanded;
        }
        Optional<DataRecord> record = variable.get().value();
        if (record.isEmpty() || record.get().rowCount() == 0) {
            return expanded;
        }
        for (Map<String, Object> recordRow : record.get().rows()) {
            if (!passesExpandFilter(expand.filter(), recordRow)) {
                continue;
            }
            expanded.add(buildProjectedRow(node, recordRow, drivingAlias, spec, ruleObjectPath));
        }
        return expanded;
    }

    private Map<String, Object> buildProjectedRow(
            PlatformObject drivingNode,
            Map<String, Object> recordRow,
            String drivingAlias,
            ObjectQuerySpec spec,
            String ruleObjectPath
    ) {
        Map<String, String> aliasPaths = new LinkedHashMap<>();
        aliasPaths.put(drivingAlias, drivingNode.path());
        for (ObjectQueryJoinSpec join : spec.joinsOrEmpty()) {
            if (join.alias() == null || join.alias().isBlank()) {
                continue;
            }
            String joinType = join.type() != null ? join.type().trim().toLowerCase() : "left";
            Optional<String> joinedPath = joinResolver.resolveJoin(join, aliasPaths, drivingAlias, ruleObjectPath);
            if (joinedPath.isPresent()) {
                aliasPaths.put(join.alias(), joinedPath.get());
            } else if ("inner".equals(joinType)) {
                return Map.of();
            }
        }
        if (aliasPaths.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("_devicePath", drivingNode.path());
        if (recordRow != null && spec.from().expand() != null) {
            String rowKeyField = spec.from().expand().rowKey();
            Object rowKey = rowKeyField != null ? recordRow.get(rowKeyField) : null;
            projected.put("_rowKey", rowKey != null ? drivingNode.path() + "#" + rowKey : drivingNode.path());
        } else {
            projected.put("_rowKey", drivingNode.path());
        }
        for (ObjectQueryFieldSpec field : spec.fieldsOrEmpty()) {
            projected.put(
                    field.name(),
                    resolveFieldValue(field, aliasPaths, recordRow, ruleObjectPath, drivingNode, projected)
            );
        }
        return projected;
    }

    private Object resolveFieldValue(
            ObjectQueryFieldSpec field,
            Map<String, String> aliasPaths,
            Map<String, Object> recordRow,
            String ruleObjectPath,
            PlatformObject drivingNode,
            Map<String, Object> projectedRow
    ) {
        if (field.historianFn() != null && !field.historianFn().isBlank() && field.ref() != null && !field.ref().isBlank()) {
            String resolvedRef = ObjectQueryRefTemplate.substitute(field.ref(), aliasPaths, recordRow);
            try {
                PlatformRef ref = PlatformRefParser.parse(resolvedRef);
                return historianColumnResolver.resolve(field.historianFn(), field.historianWindow(), ref);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (field.ref() != null && !field.ref().isBlank()) {
            if (ObjectQueryRefTemplate.isRowFieldRef(field.ref())) {
                String rowField = ObjectQueryRefTemplate.rowFieldName(field.ref());
                return recordRow != null ? recordRow.get(rowField) : null;
            }
            String resolvedRef = ObjectQueryRefTemplate.substitute(field.ref(), aliasPaths, recordRow);
            return platformRefExecutor.read(PlatformRefParser.parse(resolvedRef), ruleObjectPath).orElse(null);
        }
        if (field.expression() != null && !field.expression().isBlank()) {
            Map<String, Object> context = new LinkedHashMap<>(projectedRow);
            if (recordRow != null) {
                context.put("row", recordRow);
            }
            return expressionEngine.evaluate(field.expression(), drivingNode, context);
        }
        String source = field.source();
        String alias = field.alias() != null && !field.alias().isBlank() ? field.alias() : aliasPaths.keySet().iterator().next();
        String objectPath = aliasPaths.get(alias);
        if (objectPath == null) {
            return null;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        if (node == null) {
            return null;
        }
        if (source == null || source.isBlank() || "path".equals(source)) {
            return node.path();
        }
        if ("type".equals(source)) {
            return node.type().name();
        }
        if ("displayName".equals(source)) {
            return node.displayName();
        }
        if ("description".equals(source)) {
            return node.description();
        }
        if ("variables".equalsIgnoreCase(source)) {
            return node.variables().keySet().stream().sorted().toList();
        }
        return node.getVariable(source)
                .flatMap(Variable::value)
                .map(record -> record.rowCount() > 0 ? record.firstRow() : Map.of())
                .orElse(null);
    }

    private boolean passesFilter(String filterExpression, PlatformObject node) {
        if (filterExpression == null || filterExpression.isBlank()) {
            return true;
        }
        Object result = expressionEngine.evaluate(filterExpression, node);
        return !(result instanceof Boolean bool) || bool;
    }

    private boolean passesExpandFilter(String filterExpression, Map<String, Object> recordRow) {
        if (filterExpression == null || filterExpression.isBlank()) {
            return true;
        }
        Map<String, Object> context = Map.of("row", recordRow);
        Object result = expressionEngine.evaluate(filterExpression, null, context);
        return !(result instanceof Boolean bool) || bool;
    }

    private static boolean matchesObjectTypes(PlatformObject node, Set<String> objectTypes) {
        if (objectTypes == null || objectTypes.isEmpty()) {
            return true;
        }
        return objectTypes.contains(node.type().name());
    }

    private List<Map<String, Object>> applyGroupBy(List<Map<String, Object>> rows, ObjectQuerySpec spec) {
        List<String> groupBy = spec.groupBy();
        List<ObjectQueryAggregateSpec> aggregates = spec.aggregates();
        if (groupBy == null || groupBy.isEmpty() || aggregates == null || aggregates.isEmpty()) {
            return rows;
        }
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            String key = groupKey(row, groupBy);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> grouped = new ArrayList<>();
        for (List<Map<String, Object>> bucket : groups.values()) {
            if (bucket.isEmpty()) {
                continue;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            Map<String, Object> sample = bucket.getFirst();
            for (String field : groupBy) {
                out.put(field, sample.get(field));
            }
            for (ObjectQueryAggregateSpec aggregate : aggregates) {
                String name = aggregate.name() != null && !aggregate.name().isBlank()
                        ? aggregate.name()
                        : aggregate.fn() + "_" + aggregate.field();
                out.put(name, aggregateBucket(bucket, aggregate));
            }
            grouped.add(out);
        }
        return grouped;
    }

    private static String groupKey(Map<String, Object> row, List<String> groupBy) {
        StringBuilder key = new StringBuilder();
        for (String field : groupBy) {
            if (!key.isEmpty()) {
                key.append('\u0001');
            }
            key.append(String.valueOf(row.get(field)));
        }
        return key.toString();
    }

    private static Object aggregateBucket(List<Map<String, Object>> bucket, ObjectQueryAggregateSpec aggregate) {
        String fn = aggregate.fn() != null ? aggregate.fn().trim().toLowerCase() : "count";
        String field = aggregate.field();
        return switch (fn) {
            case "count" -> bucket.size();
            case "sum" -> sumField(bucket, requireField(field));
            case "avg" -> avgField(bucket, requireField(field));
            case "min" -> minField(bucket, requireField(field));
            case "max" -> maxField(bucket, requireField(field));
            case "first" -> firstField(bucket, requireField(field));
            default -> throw new IllegalArgumentException("Unsupported OQ group aggregate: " + aggregate.fn());
        };
    }

    private List<Map<String, Object>> applyHaving(List<Map<String, Object>> rows, String having) {
        if (having == null || having.isBlank()) {
            return rows.stream().filter(row -> !row.isEmpty()).toList();
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            Object result = expressionEngine.evaluate(having, null, row);
            if (!(result instanceof Boolean bool) || bool) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private List<Map<String, Object>> sortRows(List<Map<String, Object>> rows, List<ObjectQueryOrderSpec> orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return rows;
        }
        Comparator<Map<String, Object>> comparator = null;
        for (ObjectQueryOrderSpec order : orderBy) {
            if (order.field() == null) {
                continue;
            }
        Comparator<Map<String, Object>> next = (left, right) -> compareValues(left.get(order.field()), right.get(order.field()));
            if ("desc".equalsIgnoreCase(order.dir())) {
                next = next.reversed();
            }
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        if (comparator == null) {
            return rows;
        }
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);
        return sorted;
    }

    private static List<Map<String, Object>> paginate(List<Map<String, Object>> rows, Integer limit, Integer offset) {
        int start = offset != null && offset > 0 ? offset : 0;
        int end = limit != null && limit >= 0 ? Math.min(start + limit, rows.size()) : rows.size();
        if (start >= rows.size()) {
            return List.of();
        }
        return rows.subList(start, end);
    }

    private static String requireField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("OQ aggregate requires field name");
        }
        return field;
    }

    private static double sumField(List<Map<String, Object>> rows, String field) {
        double sum = 0;
        for (Map<String, Object> row : rows) {
            sum += toDouble(row.get(field));
        }
        return sum;
    }

    private static double avgField(List<Map<String, Object>> rows, String field) {
        if (rows.isEmpty()) {
            return 0;
        }
        return sumField(rows, field) / rows.size();
    }

    private static double minField(List<Map<String, Object>> rows, String field) {
        return rows.stream().mapToDouble(row -> toDouble(row.get(field))).min().orElse(0);
    }

    private static double maxField(List<Map<String, Object>> rows, String field) {
        return rows.stream().mapToDouble(row -> toDouble(row.get(field))).max().orElse(0);
    }

    private static Object firstField(List<Map<String, Object>> rows, String field) {
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0).get(field);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        if (left instanceof Comparable comparable) {
            try {
                return comparable.compareTo(right);
            } catch (ClassCastException ignored) {
                return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
            }
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }
}
