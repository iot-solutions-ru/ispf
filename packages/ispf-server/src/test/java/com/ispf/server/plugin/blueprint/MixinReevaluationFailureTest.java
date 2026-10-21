package com.ispf.server.plugin.blueprint;

import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.MixinReevaluationTrigger;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MixinReevaluationFailureTest {

    @Test
    void createFailureIsStoredOnTheObject() {
        String path = "root.platform.devices.mixin-fail";
        MixinReevaluationService service = mock(MixinReevaluationService.class);
        WatchMixinIndex index = mock(WatchMixinIndex.class);
        when(index.watchers(MixinReevaluationTrigger.OBJECT_CREATED))
                .thenReturn(List.of(mock(BlueprintDefinition.class)));
        doThrow(new IllegalStateException("mixin boom")).when(service).onObjectCreated(path);
        MixinReevaluationListener listener = new MixinReevaluationListener(service, index);

        listener.handle(ObjectChangeEvent.of(ObjectChangeType.CREATED, path));

        verify(service).recordCreateFailure(path, "mixin boom");
    }

    @Test
    void recordCreateFailureWritesTheStatusVariable() {
        ObjectManager objectManager = mock(ObjectManager.class);
        MixinReevaluationService service = new MixinReevaluationService(null, null, null, null, objectManager);

        service.recordCreateFailure("root.platform.devices.mixin-fail", "mixin boom");

        verify(objectManager).upsertSystemVariable(
                eq("root.platform.devices.mixin-fail"),
                eq("mixinReevaluationError"),
                any(),
                any()
        );
    }

    @Test
    void successfulCreateDoesNotStoreAnError() {
        String path = "root.platform.devices.mixin-ok";
        MixinReevaluationService service = mock(MixinReevaluationService.class);
        WatchMixinIndex index = mock(WatchMixinIndex.class);
        when(index.watchers(MixinReevaluationTrigger.OBJECT_CREATED))
                .thenReturn(List.of(mock(BlueprintDefinition.class)));
        MixinReevaluationListener listener = new MixinReevaluationListener(service, index);

        listener.handle(ObjectChangeEvent.of(ObjectChangeType.CREATED, path));

        verify(service, never()).recordCreateFailure(any(), any());
    }
}
