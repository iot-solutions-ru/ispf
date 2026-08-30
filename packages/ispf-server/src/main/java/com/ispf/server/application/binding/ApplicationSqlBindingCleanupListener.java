package com.ispf.server.application.binding;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Disables application SQL bindings and tree SQL bindings when their target object
 * (or subtree) is deleted — hygiene counterpart to {@code BindingPeriodicScheduleListener}.
 */
@Component
public class ApplicationSqlBindingCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSqlBindingCleanupListener.class);

    private final ApplicationSqlBindingStore bindingStore;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final ApplicationSqlBindingEventIndex eventIndex;

    public ApplicationSqlBindingCleanupListener(
            ApplicationSqlBindingStore bindingStore,
            SqlBindingObjectService sqlBindingObjectService,
            ApplicationSqlBindingEventIndex eventIndex
    ) {
        this.bindingStore = bindingStore;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.eventIndex = eventIndex;
    }

    @EventListener
    @Order(41)
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.DELETED) {
            return;
        }
        String path = event.path();
        int appDisabled = bindingStore.disableForObjectSubtree(path);
        if (appDisabled > 0) {
            eventIndex.onBindingChanged();
        }
        int treeDisabled = sqlBindingObjectService.disableForTargetSubtree(path);
        if (appDisabled > 0 || treeDisabled > 0) {
            log.info(
                    "Disabled SQL bindings for deleted subtree {}: application={}, tree={}",
                    path,
                    appDisabled,
                    treeDisabled
            );
        }
    }
}
