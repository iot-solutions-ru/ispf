package com.ispf.server.alert;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.AlertRuleRuntimeProperties;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleRuntimeFlusherTest {

    private static final String PATH = "root.automation.alert-rules.flush";

    @Mock
    ObjectManager objectManager;
    @Mock
    ObjectTree tree;

    private final AlertRuleRuntimeStore store = new AlertRuleRuntimeStore();
    private AlertRuleRuntimeFlusher flusher;

    @BeforeEach
    void setUp() {
        flusher = new AlertRuleRuntimeFlusher(store, objectManager, new AlertRuleRuntimeProperties());
        PlatformObject node = new PlatformObject(PATH, PATH, ObjectType.ALERT, "flush", "", null);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.findByPath(PATH)).thenReturn(Optional.of(node));
        when(objectManager.require(PATH)).thenReturn(node);
    }

    @Test
    void flushedRuleIsClean() {
        store.setLastConditionMet(PATH, true);

        flusher.flushDirty();

        verify(objectManager).persistNodeTree(PATH);
        assertFalse(store.isDirty(PATH));
    }

    @Test
    void writeDuringThePersistIsFlushedNextTime() {
        store.setLastConditionMet(PATH, true);
        doAnswer(invocation -> {
            store.setLatchedActive(PATH, true);
            return null;
        }).doNothing().when(objectManager).persistNodeTree(PATH);

        flusher.flushDirty();

        assertTrue(store.isDirty(PATH));

        flusher.flushDirty();

        verify(objectManager, times(2)).persistNodeTree(PATH);
        ArgumentCaptor<DataRecord> latched = ArgumentCaptor.forClass(DataRecord.class);
        verify(objectManager, times(2)).setSystemVariableValue(eq(PATH), eq("latchedActive"), latched.capture());
        assertEquals(true, latched.getAllValues().get(1).firstRow().get("value"));
        assertFalse(store.isDirty(PATH));
    }

    @Test
    void failedPersistKeepsTheRuleDirty() {
        store.setLastConditionMet(PATH, true);
        doThrow(new IllegalStateException("database unavailable")).when(objectManager).persistNodeTree(PATH);

        flusher.flushDirty();

        assertTrue(store.isDirty(PATH));
    }
}
