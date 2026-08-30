package com.ispf.server.application.binding;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationSqlBindingCleanupListenerTest {

    private static final String PATH = "root.platform.devices.gone";

    @Mock
    ApplicationSqlBindingStore bindingStore;
    @Mock
    SqlBindingObjectService sqlBindingObjectService;
    @Mock
    ApplicationSqlBindingEventIndex eventIndex;

    private ApplicationSqlBindingCleanupListener listener;

    @BeforeEach
    void setUp() {
        listener = new ApplicationSqlBindingCleanupListener(
                bindingStore,
                sqlBindingObjectService,
                eventIndex
        );
    }

    @Test
    void deletedPathDisablesAppAndTreeBindingsAndRebuildsIndex() {
        when(bindingStore.disableForObjectSubtree(PATH)).thenReturn(1);
        when(sqlBindingObjectService.disableForTargetSubtree(PATH)).thenReturn(2);

        listener.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.DELETED, PATH));

        verify(bindingStore).disableForObjectSubtree(PATH);
        verify(eventIndex).onBindingChanged();
        verify(sqlBindingObjectService).disableForTargetSubtree(PATH);
    }

    @Test
    void deletedPathWithNoAppBindingsSkipsIndexRebuild() {
        when(bindingStore.disableForObjectSubtree(PATH)).thenReturn(0);
        when(sqlBindingObjectService.disableForTargetSubtree(PATH)).thenReturn(1);

        listener.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.DELETED, PATH));

        verify(eventIndex, never()).onBindingChanged();
        verify(sqlBindingObjectService).disableForTargetSubtree(PATH);
    }

    @Test
    void nonDeletedEventsAreIgnored() {
        listener.onObjectChange(ObjectChangeEvent.of(ObjectChangeType.UPDATED, PATH));

        verifyNoInteractions(bindingStore, sqlBindingObjectService, eventIndex);
    }
}
