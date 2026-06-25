package com.ispf.server.alert;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.AlertRuleRuntimeProperties;
import com.ispf.server.object.ObjectManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
public class AlertRuleRuntimeFlusher {

    private static final Logger log = LoggerFactory.getLogger(AlertRuleRuntimeFlusher.class);

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final AlertRuleRuntimeStore runtimeStore;
    private final ObjectManager objectManager;
    private final AlertRuleRuntimeProperties properties;

    public AlertRuleRuntimeFlusher(
            AlertRuleRuntimeStore runtimeStore,
            ObjectManager objectManager,
            AlertRuleRuntimeProperties properties
    ) {
        this.runtimeStore = runtimeStore;
        this.objectManager = objectManager;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ispf.alert-rule.runtime.flush-interval-ms:30000}")
    public void scheduledFlush() {
        if (!properties.isFlushEnabled()) {
            return;
        }
        flushDirty();
    }

    @PreDestroy
    public void shutdownFlush() {
        flushDirty();
    }

    public void flushDirty() {
        List<String> dirtyPaths = runtimeStore.drainDirtyPaths();
        if (dirtyPaths.isEmpty()) {
            return;
        }
        int flushed = 0;
        for (String path : dirtyPaths) {
            try {
                if (flushOne(path)) {
                    flushed++;
                }
            } catch (Exception ex) {
                log.warn("Failed to flush alert rule runtime state for {}: {}", path, ex.getMessage());
            }
        }
        if (flushed > 0) {
            log.debug("Flushed alert rule runtime state for {} rule(s)", flushed);
        }
    }

    @Transactional
    public void flushNow(String path) {
        flushOne(path);
    }

    private boolean flushOne(String path) {
        if (!runtimeStore.isDirty(path)) {
            return false;
        }
        if (objectManager.tree().findByPath(path).isEmpty()) {
            runtimeStore.remove(path);
            return false;
        }
        AlertRuleRuntimeState state = runtimeStore.snapshotForPersist(path);
        applyRuntimeState(path, state);
        objectManager.persistNodeTree(path);
        runtimeStore.markClean(path);
        return true;
    }

    private void applyRuntimeState(String path, AlertRuleRuntimeState state) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable("lastFiredAt").isEmpty()) {
            setString(path, "lastFiredAt", "");
        }
        setRuntimeBoolean(path, "lastConditionMet", Boolean.TRUE.equals(state.lastConditionMet()));
        setRuntimeString(path, "lastWatchValue", state.lastWatchValue() != null ? Double.toString(state.lastWatchValue()) : "");
        setRuntimeString(path, "lastFiredAt", state.lastFiredAt() != null ? state.lastFiredAt().toString() : "");
        setRuntimeString(path, "conditionTrueSince", state.conditionTrueSince() != null ? state.conditionTrueSince().toString() : "");
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_VALUE, Map.of("value", value != null ? value : "")));
    }

    private void setRuntimeBoolean(String path, String variable, boolean value) {
        objectManager.setSystemVariableValue(path, variable, DataRecord.single(BOOLEAN_VALUE, Map.of("value", value)));
    }

    private void setRuntimeString(String path, String variable, String value) {
        objectManager.upsertSystemVariable(
                path,
                variable,
                STRING_VALUE,
                DataRecord.single(STRING_VALUE, Map.of("value", value != null ? value : ""))
        );
    }
}
