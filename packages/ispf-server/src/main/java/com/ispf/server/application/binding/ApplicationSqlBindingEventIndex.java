package com.ispf.server.application.binding;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * In-memory index for {@code on_event} SQL bindings (ADR-0024 demand-driven gate).
 */
@Component
public class ApplicationSqlBindingEventIndex {

    private final ApplicationSqlBindingStore store;
    private volatile Set<String> keys = Set.of();

    public ApplicationSqlBindingEventIndex(ApplicationSqlBindingStore store) {
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        rebuild();
    }

    public synchronized void rebuild() {
        Set<String> next = new HashSet<>();
        for (ApplicationSqlBindingStore.SqlBinding binding : store.listAllOnEvent()) {
            next.add(key(binding.objectPath(), binding.triggerFunctionName()));
        }
        keys = Set.copyOf(next);
    }

    public void onBindingChanged() {
        rebuild();
    }

    public boolean hasBindings(String objectPath, String eventName) {
        if (objectNameBlank(objectPath) || eventNameBlank(eventName)) {
            return false;
        }
        return keys.contains(key(objectPath, eventName));
    }

    static String key(String objectPath, String eventName) {
        return objectPath + "\0" + eventName;
    }

    private static boolean objectNameBlank(String objectPath) {
        return objectPath == null || objectPath.isBlank();
    }

    private static boolean eventNameBlank(String eventName) {
        return eventName == null || eventName.isBlank();
    }
}
