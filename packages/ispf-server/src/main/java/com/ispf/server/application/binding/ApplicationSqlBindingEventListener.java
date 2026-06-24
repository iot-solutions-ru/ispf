package com.ispf.server.application.binding;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class ApplicationSqlBindingEventListener implements ObjectChangeAsyncHandler {

    private final ApplicationSqlBindingStore store;
    private final ApplicationSqlBindingService bindingService;

    public ApplicationSqlBindingEventListener(
            ApplicationSqlBindingStore store,
            ApplicationSqlBindingService bindingService
    ) {
        this.store = store;
        this.bindingService = bindingService;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
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
