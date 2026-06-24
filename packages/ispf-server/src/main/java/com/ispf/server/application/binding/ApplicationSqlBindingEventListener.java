package com.ispf.server.application.binding;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ApplicationSqlBindingEventListener {

    private final ApplicationSqlBindingStore store;
    private final ApplicationSqlBindingService bindingService;

    public ApplicationSqlBindingEventListener(
            ApplicationSqlBindingStore store,
            ApplicationSqlBindingService bindingService
    ) {
        this.store = store;
        this.bindingService = bindingService;
    }

    @Async("objectChangeExecutor")
    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.EVENT_FIRED || event.variableName() == null) {
            return;
        }
        for (ApplicationSqlBindingStore.SqlBinding binding : store.listForEvent(
                event.path(),
                event.variableName()
        )) {
            bindingService.refreshBinding(binding);
        }
    }
}
