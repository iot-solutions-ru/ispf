package com.ispf.server.query.oq;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;
import com.ispf.expression.ExpressionEngine;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.query.ObjectPathPattern;
import com.ispf.server.ref.PlatformRefExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ObjectQueryJoinResolver {

    private final ObjectManager objectManager;
    private final PlatformRefExecutor platformRefExecutor;

    public ObjectQueryJoinResolver(ObjectManager objectManager, PlatformRefExecutor platformRefExecutor) {
        this.objectManager = objectManager;
        this.platformRefExecutor = platformRefExecutor;
    }

    public Optional<String> resolveJoin(
            ObjectQueryJoinSpec join,
            Map<String, String> aliasPaths,
            String drivingAlias,
            String ruleObjectPath
    ) {
        if (join == null || join.on() == null) {
            return Optional.empty();
        }
        String leftPath = aliasPaths.get(drivingAlias);
        if (leftPath == null) {
            return Optional.empty();
        }
        JoinKind kind = join.on().kind() != null ? join.on().kind() : JoinKind.PARENT;
        return switch (kind) {
            case PARENT -> parentPath(leftPath).flatMap(this::findJoinCandidate);
            case SAME_OBJECT -> Optional.of(leftPath);
            case REF -> resolveRefJoin(join, join.on(), aliasPaths, drivingAlias, ruleObjectPath);
            case EQ -> resolveEqJoin(join, join.on(), aliasPaths, drivingAlias, ruleObjectPath);
            case PATH_PREFIX, ANCESTOR -> resolveScanJoin(join, leftPath, join.on());
            case LOOKUP -> resolveLookupJoin(join, join.on(), aliasPaths, drivingAlias, ruleObjectPath);
            case PATH_SUBSTRING -> resolvePathSubstringJoin(join, leftPath, join.on());
        };
    }

    private Optional<String> resolveRefJoin(
            ObjectQueryJoinSpec join,
            ObjectQueryJoinOnSpec on,
            Map<String, String> aliasPaths,
            String drivingAlias,
            String ruleObjectPath
    ) {
        if (on.left() == null || on.left().isBlank()) {
            return Optional.empty();
        }
        String leftRef = ObjectQueryRefTemplate.substitute(on.left(), aliasPaths, Map.of());
        PlatformRef ref = PlatformRefParser.parse(leftRef);
        Optional<Object> value = platformRefExecutor.read(ref, ruleObjectPath);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        String targetPath = String.valueOf(value.get()).trim();
        if (targetPath.isBlank()) {
            return Optional.empty();
        }
        return findJoinCandidate(targetPath).filter(path -> matchesJoinSource(join, path));
    }

    private Optional<String> resolveEqJoin(
            ObjectQueryJoinSpec join,
            ObjectQueryJoinOnSpec on,
            Map<String, String> aliasPaths,
            String drivingAlias,
            String ruleObjectPath
    ) {
        if (on.left() == null || on.right() == null) {
            return Optional.empty();
        }
        String leftRef = ObjectQueryRefTemplate.substitute(on.left(), aliasPaths, Map.of());
        Optional<Object> leftValue = platformRefExecutor.read(PlatformRefParser.parse(leftRef), ruleObjectPath);
        if (leftValue.isEmpty()) {
            return Optional.empty();
        }
        for (PlatformObject candidate : scanJoinCandidates(join)) {
            String rightRef = ObjectQueryRefTemplate.substitute(
                    on.right(),
                    Map.of(join.alias(), candidate.path()),
                    Map.of()
            );
            Optional<Object> rightValue = platformRefExecutor.read(PlatformRefParser.parse(rightRef), ruleObjectPath);
            if (rightValue.isPresent() && valuesEqual(leftValue.get(), rightValue.get())) {
                return Optional.of(candidate.path());
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolveScanJoin(ObjectQueryJoinSpec join, String leftPath, ObjectQueryJoinOnSpec on) {
        JoinKind kind = on != null && on.kind() != null ? on.kind() : JoinKind.PATH_PREFIX;
        for (PlatformObject candidate : scanJoinCandidates(join)) {
            if (kind == JoinKind.PATH_PREFIX && candidate.path().startsWith(leftPath + ".")) {
                return Optional.of(candidate.path());
            }
            if (kind == JoinKind.ANCESTOR && leftPath.startsWith(candidate.path() + ".")) {
                return Optional.of(candidate.path());
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolvePathSubstringJoin(
            ObjectQueryJoinSpec join,
            String leftPath,
            ObjectQueryJoinOnSpec on
    ) {
        if (on == null || on.match() == null || on.match().isBlank()) {
            return Optional.empty();
        }
        String segment = extractPathSegment(leftPath, on.match());
        if (segment == null || segment.isBlank()) {
            return Optional.empty();
        }
        for (PlatformObject candidate : scanJoinCandidates(join)) {
            if (candidate.path().contains(segment)) {
                return Optional.of(candidate.path());
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolveLookupJoin(
            ObjectQueryJoinSpec join,
            ObjectQueryJoinOnSpec on,
            Map<String, String> aliasPaths,
            String drivingAlias,
            String ruleObjectPath
    ) {
        if (on == null) {
            return Optional.empty();
        }
        String lookupKey;
        if (on.left() != null && !on.left().isBlank()) {
            String leftRef = ObjectQueryRefTemplate.substitute(on.left(), aliasPaths, Map.of());
            Optional<Object> leftValue = platformRefExecutor.read(PlatformRefParser.parse(leftRef), ruleObjectPath);
            if (leftValue.isEmpty()) {
                return Optional.empty();
            }
            lookupKey = String.valueOf(leftValue.get()).trim();
        } else if (on.match() != null && !on.match().isBlank()) {
            lookupKey = on.match().trim();
        } else {
            return Optional.empty();
        }
        if (lookupKey.isBlank()) {
            return Optional.empty();
        }
        String pattern = on.catalogPathPattern() != null && !on.catalogPathPattern().isBlank()
                ? on.catalogPathPattern()
                : join.sourcePathPattern();
        ObjectQueryJoinSpec scanJoin = pattern != null && !pattern.isBlank()
                ? new ObjectQueryJoinSpec(join.alias(), join.type(), pattern, join.objectTypes(), join.filter(), join.on())
                : join;
        for (PlatformObject candidate : scanJoinCandidates(scanJoin)) {
            String leaf = leafName(candidate.path());
            if (lookupKey.equals(leaf) || lookupKey.equals(candidate.displayName())) {
                return Optional.of(candidate.path());
            }
        }
        return Optional.empty();
    }

    private static String extractPathSegment(String leftPath, String matchPattern) {
        try {
            Matcher matcher = Pattern.compile(matchPattern).matcher(leftPath);
            if (!matcher.find()) {
                return null;
            }
            return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        } catch (RuntimeException ex) {
            int index = leftPath.indexOf(matchPattern);
            return index >= 0 ? matchPattern : null;
        }
    }

    private static String leafName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private List<PlatformObject> scanJoinCandidates(ObjectQueryJoinSpec join) {
        List<PlatformObject> candidates = new ArrayList<>();
        String pattern = join.sourcePathPattern();
        Set<String> types = join.objectTypes();
        for (PlatformObject node : objectManager.tree().all()) {
            if (pattern != null && !pattern.isBlank() && !ObjectPathPattern.matches(node.path(), pattern)) {
                continue;
            }
            if (types != null && !types.isEmpty() && !types.contains(node.type().name())) {
                continue;
            }
            candidates.add(node);
        }
        return candidates;
    }

    private boolean matchesJoinSource(ObjectQueryJoinSpec join, String path) {
        if (join.sourcePathPattern() != null
                && !join.sourcePathPattern().isBlank()
                && !ObjectPathPattern.matches(path, join.sourcePathPattern())) {
            return false;
        }
        if (join.objectTypes() != null && !join.objectTypes().isEmpty()) {
            return objectManager.tree().findByPath(path)
                    .map(node -> join.objectTypes().contains(node.type().name()))
                    .orElse(false);
        }
        return true;
    }

    private Optional<String> findJoinCandidate(String path) {
        return objectManager.tree().findByPath(path).map(PlatformObject::path);
    }

    private static Optional<String> parentPath(String path) {
        if (path == null || "root".equals(path)) {
            return Optional.empty();
        }
        int dot = path.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        return Optional.of(path.substring(0, dot));
    }

    private static boolean valuesEqual(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }
}
