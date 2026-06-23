package com.ispf.server.object;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class VisualGroupCleanupListener {

    private final VisualGroupService visualGroupService;

    public VisualGroupCleanupListener(VisualGroupService visualGroupService) {
        this.visualGroupService = visualGroupService;
    }

    @EventListener
    public void onObjectDeleted(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.DELETED) {
            return;
        }
        visualGroupService.removePathFromAllGroups(event.path());
    }
}
