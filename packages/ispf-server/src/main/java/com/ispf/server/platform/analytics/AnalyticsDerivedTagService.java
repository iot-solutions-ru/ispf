package com.ispf.server.platform.analytics;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.engine.AnalyticsDerivedValueWriter;
import com.ispf.server.platform.analytics.frames.EventFrameService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Computes runtime derived-tag variables on devices with analytics MIXIN blueprints (BL-160).
 */
@Service
public class AnalyticsDerivedTagService {

    static final Set<String> ANALYTICS_BLUEPRINT_NAMES = Set.of(
            AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL,
            AnalyticsBlueprintBootstrap.RATE_OF_CHANGE_MODEL,
            AnalyticsBlueprintBootstrap.OEE_MODEL
    );

    private final ObjectManager objectManager;
    private final VariableHistoryService variableHistoryService;
    private final AnalyticsDerivedValueWriter derivedValueWriter;
    private final EventFrameService eventFrameService;
    private final VariableMemberAccessService variableMemberAccessService;

    public AnalyticsDerivedTagService(
            ObjectManager objectManager,
            VariableHistoryService variableHistoryService,
            AnalyticsDerivedValueWriter derivedValueWriter,
            EventFrameService eventFrameService,
            VariableMemberAccessService variableMemberAccessService
    ) {
        this.objectManager = objectManager;
        this.variableHistoryService = variableHistoryService;
        this.derivedValueWriter = derivedValueWriter;
        this.eventFrameService = eventFrameService;
        this.variableMemberAccessService = variableMemberAccessService;
    }

    @Transactional(readOnly = true)
    public List<String> listDerivedDevicePaths() {
        List<String> paths = new ArrayList<>();
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.DEVICE) {
                continue;
            }
            if (isDerivedTagDevice(node)) {
                paths.add(node.path());
            }
        }
        return paths;
    }

    @Transactional
    public DerivedTagRefreshResult refreshDevice(String devicePath) {
        return refreshDevice(devicePath, Instant.now(), null, false);
    }

    @Transactional
    public DerivedTagRefreshResult refreshDevice(String devicePath, Authentication authentication) {
        return refreshDevice(devicePath, Instant.now(), authentication, true);
    }

    @Transactional
    public DerivedTagRefreshResult refreshDevice(String devicePath, Instant observedAt) {
        return refreshDevice(devicePath, observedAt, null, false);
    }

    private DerivedTagRefreshResult refreshDevice(
            String devicePath,
            Instant observedAt,
            Authentication authentication,
            boolean enforceMemberAcl
    ) {
        PlatformObject node = objectManager.require(devicePath);
        if (node.type() != ObjectType.DEVICE) {
            throw new IllegalArgumentException("Not a device: " + devicePath);
        }
        if (!isDerivedTagDevice(node)) {
            return new DerivedTagRefreshResult(devicePath, "skipped", "No analytics derived-tag variables");
        }
        if (node.getVariable("oeePct").isPresent()) {
            return refreshOee(node, observedAt, authentication, enforceMemberAcl);
        }
        return refreshScalarDerived(node, observedAt, authentication, enforceMemberAcl);
    }

    @Transactional
    public int refreshAllEnabled() {
        int updated = 0;
        for (String path : listDerivedDevicePaths()) {
            DerivedTagRefreshResult result = refreshDevice(path);
            if ("ok".equals(result.status())) {
                updated++;
            }
        }
        return updated;
    }

    private DerivedTagRefreshResult refreshScalarDerived(
            PlatformObject node,
            Instant observedAt,
            Authentication authentication,
            boolean enforceMemberAcl
    ) {
        String sourcePath = readString(node, "sourcePath").filter(s -> !s.isBlank()).orElse(node.path());
        String sourceVariable = readString(node, "sourceVariable").orElse("");
        if (sourceVariable.isBlank()) {
            return new DerivedTagRefreshResult(node.path(), "skipped", "sourceVariable is empty");
        }
        String sourceField = readString(node, "sourceField").orElse("value");
        String windowBucket = readString(node, "windowBucket").orElse("5m");
        String blueprintName = resolveAnalyticsBlueprintName(node);

        Instant to = observedAt != null ? observedAt : Instant.now();
        Instant from = to.minus(24, ChronoUnit.HOURS);
        if (enforceMemberAcl) {
            variableMemberAccessService.requireRead(sourcePath, sourceVariable, authentication);
        }
        VariableHistoryService.VariableHistoryAggregateResponse aggregate = variableHistoryService.aggregate(
                sourcePath,
                sourceVariable,
                sourceField,
                from,
                to,
                windowBucket,
                48
        );
        List<VariableHistoryService.VariableHistoryBucket> buckets = aggregate.buckets();
        if (buckets.isEmpty()) {
            return new DerivedTagRefreshResult(node.path(), "skipped", "No historian buckets");
        }

        double computed;
        if (AnalyticsBlueprintBootstrap.RATE_OF_CHANGE_MODEL.equals(blueprintName)) {
            VariableHistoryService.VariableHistoryBucket first = buckets.get(0);
            VariableHistoryService.VariableHistoryBucket last = buckets.get(buckets.size() - 1);
            double firstAvg = first.avg() != null ? first.avg() : 0.0;
            double lastAvg = last.avg() != null ? last.avg() : 0.0;
            computed = lastAvg - firstAvg;
        } else {
            VariableHistoryService.VariableHistoryBucket last = buckets.get(buckets.size() - 1);
            computed = last.avg() != null ? last.avg() : 0.0;
        }

        writeString(node.path(), "derivedValue", formatNumber(computed), observedAt);
        return new DerivedTagRefreshResult(node.path(), "ok", "derivedValue=" + formatNumber(computed));
    }

    private DerivedTagRefreshResult refreshOee(
            PlatformObject node,
            Instant observedAt,
            Authentication authentication,
            boolean enforceMemberAcl
    ) {
        String sourcePath = readString(node, "sourcePath").filter(s -> !s.isBlank()).orElse(node.path());
        String windowBucket = readString(node, "windowBucket").orElse("8h");
        String sourceField = readString(node, "sourceField").orElse("value");
        String eventFrameScope = readString(node, "eventFrameScope").filter(s -> !s.isBlank()).orElse(sourcePath);
        String availabilityVar = readString(node, "availabilityVariable").orElse("");
        String performanceVar = readString(node, "performanceVariable").orElse("");
        String qualityVar = readString(node, "qualityVariable").orElse("");
        if (availabilityVar.isBlank() || performanceVar.isBlank() || qualityVar.isBlank()) {
            return new DerivedTagRefreshResult(node.path(), "skipped", "OEE source variables not configured");
        }
        if (enforceMemberAcl) {
            variableMemberAccessService.requireRead(sourcePath, availabilityVar, authentication);
            variableMemberAccessService.requireRead(sourcePath, performanceVar, authentication);
            variableMemberAccessService.requireRead(sourcePath, qualityVar, authentication);
        }

        Instant to = observedAt != null ? observedAt : Instant.now();
        Instant from = to.minus(24, ChronoUnit.HOURS);
        var shiftWindow = eventFrameService.resolveShiftWindow(eventFrameScope, to);
        if (shiftWindow.isPresent()) {
            from = shiftWindow.get().from();
            to = shiftWindow.get().to();
        }
        double availability = latestBucketAvg(sourcePath, availabilityVar, sourceField, from, to, windowBucket);
        double performance = latestBucketAvg(sourcePath, performanceVar, sourceField, from, to, windowBucket);
        double quality = latestBucketAvg(sourcePath, qualityVar, sourceField, from, to, windowBucket);
        double oee = (availability / 100.0) * (performance / 100.0) * (quality / 100.0) * 100.0;

        writeString(node.path(), "availabilityPct", formatNumber(availability), observedAt);
        writeString(node.path(), "performancePct", formatNumber(performance), observedAt);
        writeString(node.path(), "qualityPct", formatNumber(quality), observedAt);
        writeString(node.path(), "oeePct", formatNumber(oee), observedAt);
        return new DerivedTagRefreshResult(node.path(), "ok", "oeePct=" + formatNumber(oee));
    }

    private double latestBucketAvg(
            String objectPath,
            String variableName,
            String field,
            Instant from,
            Instant to,
            String bucket
    ) {
        VariableHistoryService.VariableHistoryAggregateResponse aggregate = variableHistoryService.aggregate(
                objectPath,
                variableName,
                field,
                from,
                to,
                bucket,
                24
        );
        List<VariableHistoryService.VariableHistoryBucket> buckets = aggregate.buckets();
        if (buckets.isEmpty()) {
            return 0.0;
        }
        VariableHistoryService.VariableHistoryBucket last = buckets.get(buckets.size() - 1);
        return last.avg() != null ? last.avg() : 0.0;
    }

    private static boolean isDerivedTagDevice(PlatformObject node) {
        return node.getVariable("derivedValue").isPresent() || node.getVariable("oeePct").isPresent();
    }

    private static String resolveAnalyticsBlueprintName(PlatformObject node) {
        for (String blueprintId : node.appliedBlueprintIds()) {
            String lower = blueprintId.toLowerCase(Locale.ROOT);
            if (lower.contains("rolling-avg")) {
                return AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL;
            }
            if (lower.contains("rate-of-change")) {
                return AnalyticsBlueprintBootstrap.RATE_OF_CHANGE_MODEL;
            }
            if (lower.contains("oee")) {
                return AnalyticsBlueprintBootstrap.OEE_MODEL;
            }
        }
        return AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL;
    }

    private void writeString(String path, String variable, String value, Instant observedAt) {
        derivedValueWriter.write(path, variable, value, observedAt);
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> String.valueOf(r.firstRow().get("value")));
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public record DerivedTagRefreshResult(String devicePath, String status, String message) {
    }
}
