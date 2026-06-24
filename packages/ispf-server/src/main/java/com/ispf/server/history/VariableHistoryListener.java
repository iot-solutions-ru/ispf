package com.ispf.server.history;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class VariableHistoryListener implements ObjectChangeAsyncHandler {

    private final VariableHistoryService variableHistoryService;

    public VariableHistoryListener(VariableHistoryService variableHistoryService) {
        this.variableHistoryService = variableHistoryService;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        variableHistoryService.recordVariableUpdate(event.path(), event.variableName());
    }
}
