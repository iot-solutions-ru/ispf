package com.ispf.server.history;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class VariableHistoryListener {

    private final VariableHistoryService variableHistoryService;

    public VariableHistoryListener(VariableHistoryService variableHistoryService) {
        this.variableHistoryService = variableHistoryService;
    }

    @Async("objectChangeExecutor")
    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        variableHistoryService.recordVariableUpdate(event.path(), event.variableName());
    }
}
