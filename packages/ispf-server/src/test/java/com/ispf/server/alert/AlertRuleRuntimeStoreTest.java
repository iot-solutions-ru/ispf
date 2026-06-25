package com.ispf.server.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleRuntimeStoreTest {

    private AlertRuleRuntimeStore store;

    @BeforeEach
    void setUp() {
        store = new AlertRuleRuntimeStore();
    }

    @Test
    void hotPathUpdatesStayInMemoryUntilMarkedClean() {
        String path = "root.platform.alert-rules.hot-path";
        store.setLastConditionMet(path, true);
        store.setLastWatchValue(path, 3.0);
        store.setLastFiredAt(path, Instant.parse("2026-06-25T11:00:00Z"));

        assertTrue(store.isDirty(path));
        AlertRuleRuntimeState snapshot = store.snapshotForPersist(path);
        assertTrue(snapshot.lastConditionMet());
        assertEquals(3.0, snapshot.lastWatchValue());
        assertEquals(Instant.parse("2026-06-25T11:00:00Z"), snapshot.lastFiredAt());

        store.markClean(path);
        assertFalse(store.isDirty(path));
    }

    @Test
    void resetMarksDirtyWithClearedValues() {
        String path = "root.platform.alert-rules.reset-me";
        store.setLastConditionMet(path, true);
        store.setLastWatchValue(path, 9.0);
        store.markClean(path);

        store.reset(path);

        assertTrue(store.isDirty(path));
        AlertRuleRuntimeState snapshot = store.snapshotForPersist(path);
        assertEquals(false, snapshot.lastConditionMet());
        assertEquals(null, snapshot.lastWatchValue());
        assertEquals(null, snapshot.lastFiredAt());
        assertEquals(null, snapshot.conditionTrueSince());
    }

    @Test
    void drainDirtyPathsReturnsOnlyDirtyEntries() {
        store.setLastConditionMet("root.platform.alert-rules.a", true);
        store.setLastConditionMet("root.platform.alert-rules.b", false);
        store.markClean("root.platform.alert-rules.b");
        store.setLastWatchValue("root.platform.alert-rules.c", 1.0);

        assertEquals(
                java.util.List.of(
                        "root.platform.alert-rules.a",
                        "root.platform.alert-rules.c"
                ),
                store.drainDirtyPaths()
        );
    }
}
