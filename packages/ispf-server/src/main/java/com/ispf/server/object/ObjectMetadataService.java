package com.ispf.server.object;

import com.ispf.core.object.PlatformObject;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Display-name / description and per-node audit journal toggles.
 * Extracted from {@link ObjectManager} (ADR-0048 follow-up).
 */
@Service
public class ObjectMetadataService {

    private final ObjectManager objectManager;
    private final ObjectEntityMapper mapper;

    public ObjectMetadataService(@Lazy ObjectManager objectManager, ObjectEntityMapper mapper) {
        this.objectManager = objectManager;
        this.mapper = mapper;
    }

    @Transactional
    public PlatformObject updateInfo(String path, String displayName, String description) {
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        long revisionBefore = node.revision();
        Map<String, String> before = metadataSnapshot(node);
        node.updateInfo(displayName, description);
        objectManager.persistNodeConfig(
                node,
                "UPDATE_INFO",
                "metadata",
                mapper.auditDiff(before, metadataSnapshot(node))
        );
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    @Transactional
    public PlatformObject updateBindingAuditEnabled(String path, boolean enabled) {
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        long revisionBefore = node.revision();
        boolean before = node.bindingAuditEnabled();
        node.setBindingAuditEnabled(enabled);
        objectManager.persistNodeConfig(
                node,
                "UPDATE_BINDING_AUDIT",
                "bindingAuditEnabled",
                mapper.auditDiff(before, enabled)
        );
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    public boolean isBindingAuditEnabled(String path) {
        return objectManager.tree().findByPath(path).map(PlatformObject::bindingAuditEnabled).orElse(false);
    }

    @Transactional
    public PlatformObject updateFunctionAuditEnabled(String path, boolean enabled) {
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        long revisionBefore = node.revision();
        boolean before = node.functionAuditEnabled();
        node.setFunctionAuditEnabled(enabled);
        objectManager.persistNodeConfig(
                node,
                "UPDATE_FUNCTION_AUDIT",
                "functionAuditEnabled",
                mapper.auditDiff(before, enabled)
        );
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    public boolean isFunctionAuditEnabled(String path) {
        return objectManager.tree().findByPath(path).map(PlatformObject::functionAuditEnabled).orElse(false);
    }

    @Transactional
    public PlatformObject updateEventJournalEnabled(String path, boolean enabled) {
        objectManager.assertExpectedRevision(path);
        PlatformObject node = objectManager.tree().require(path);
        long revisionBefore = node.revision();
        boolean before = node.eventJournalEnabled();
        node.setEventJournalEnabled(enabled);
        objectManager.persistNodeConfig(
                node,
                "UPDATE_EVENT_JOURNAL",
                "eventJournalEnabled",
                mapper.auditDiff(before, enabled)
        );
        objectManager.publishConfigChange(ObjectChangeType.UPDATED, path, revisionBefore);
        return node;
    }

    public boolean isEventJournalEnabled(String path) {
        return objectManager.tree().findByPath(path).map(PlatformObject::eventJournalEnabled).orElse(false);
    }

    private static Map<String, String> metadataSnapshot(PlatformObject node) {
        Map<String, String> snapshot = new HashMap<>();
        snapshot.put("displayName", node.displayName());
        snapshot.put("description", node.description() != null ? node.description() : "");
        return snapshot;
    }
}
