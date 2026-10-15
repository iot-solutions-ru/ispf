package com.ispf.driver.opcuaserver;

import com.ispf.driver.DriverException;
import com.ispf.driver.DriverPermanentException;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeObserver;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilters;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Custom OPC UA namespace backing ISPF server variable points.
 */
final class IspfOpcUaNamespace extends ManagedNamespaceWithLifecycle {

    private static final String NAMESPACE_URI = "urn:ispf:opcua:server";

    private final Map<NodeId, UaVariableNode> variableNodes = new ConcurrentHashMap<>();
    private final Map<NodeId, String> variableValues = new ConcurrentHashMap<>();
    private final Map<DataItem, AttributeObserver> dataItemObservers = new ConcurrentHashMap<>();
    private final Map<NodeId, List<DataItem>> monitoredByNode = new ConcurrentHashMap<>();
    private final SubscriptionModel subscriptionModel;
    private BiConsumer<NodeId, String> externalWriteListener;
    private UaFolderNode variablesFolder;

    void setExternalWriteListener(BiConsumer<NodeId, String> listener) {
        this.externalWriteListener = listener;
    }

    IspfOpcUaNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);
        subscriptionModel = new SubscriptionModel(server, this);
        getLifecycleManager().addLifecycle(subscriptionModel);
        getLifecycleManager().addStartupTask(this::initializeNodes);
    }

    void ensureVariable(OpcUaServerPoint point) throws DriverException {
        if (variableNodes.containsKey(point.nodeId())) {
            return;
        }
        try {
            ensureVariablesFolder();
            UaVariableNode variableNode = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                    .setNodeId(point.nodeId())
                    .setBrowseName(new QualifiedName(getNamespaceIndex(), point.nodeIdText()))
                    .setDisplayName(LocalizedText.english(point.nodeIdText()))
                    .setDataType(Identifiers.String)
                    .setTypeDefinition(Identifiers.BaseDataVariableType)
                    .setAccessLevel(AccessLevel.READ_WRITE)
                    .setUserAccessLevel(AccessLevel.READ_WRITE)
                    .build();
            String initialValue = variableValues.computeIfAbsent(point.nodeId(), ignored -> "");
            variableNode.setValue(new DataValue(new Variant(initialValue)));
            variableNode.getFilterChain().addLast(AttributeFilters.setValue((ctx, value) -> {
                String written = variantToString(value.getValue());
                variableValues.put(point.nodeId(), written);
                BiConsumer<NodeId, String> listener = externalWriteListener;
                if (listener != null) {
                    listener.accept(point.nodeId(), written);
                }
                ctx.setAttribute(AttributeId.Value, value);
                variableNode.fireAttributeChanged(AttributeId.Value, value);
            }));
            getNodeManager().addNode(variableNode);
            variablesFolder.addOrganizes(variableNode);
            variableNodes.put(point.nodeId(), variableNode);
        } catch (Exception e) {
            throw new DriverPermanentException("Failed to create OPC UA variable node " + point.nodeId(), e);
        }
    }

    void writeValue(NodeId nodeId, String value) {
        variableValues.put(nodeId, value);
        DataValue dataValue = new DataValue(new Variant(value), StatusCode.GOOD, DateTime.now());
        UaVariableNode node = variableNodes.get(nodeId);
        if (node != null) {
            node.setValue(dataValue);
            // Same notify path as AttributeFilters.setValue (external client write).
            node.fireAttributeChanged(AttributeId.Value, dataValue);
        }
        // Directly push to monitored items (SubscriptionModel sampling alone is not enough for lab GA).
        List<DataItem> items = monitoredByNode.get(nodeId);
        if (items != null) {
            for (DataItem item : items) {
                item.setValue(dataValue);
            }
        }
    }

    String readValue(NodeId nodeId) {
        return variableValues.getOrDefault(nodeId, "");
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsCreated(dataItems);
        for (DataItem item : dataItems) {
            NodeId nodeId = item.getReadValueId().getNodeId();
            monitoredByNode.computeIfAbsent(nodeId, ignored -> new CopyOnWriteArrayList<>()).add(item);
            UaVariableNode variableNode = variableNodes.get(nodeId);
            if (variableNode == null) {
                continue;
            }
            AttributeObserver observer = (node, attributeId, value) -> {
                if (attributeId == AttributeId.Value && value instanceof DataValue dataValue) {
                    item.setValue(dataValue);
                }
            };
            variableNode.addAttributeObserver(observer);
            dataItemObservers.put(item, observer);
            Object current = variableNode.getAttribute(AttributeId.Value);
            if (current instanceof DataValue dataValue) {
                item.setValue(dataValue);
            }
        }
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        for (DataItem item : dataItems) {
            NodeId nodeId = item.getReadValueId().getNodeId();
            List<DataItem> items = monitoredByNode.get(nodeId);
            if (items != null) {
                items.remove(item);
                if (items.isEmpty()) {
                    monitoredByNode.remove(nodeId);
                }
            }
            AttributeObserver observer = dataItemObservers.remove(item);
            if (observer != null) {
                UaNode node = variableNodes.get(nodeId);
                if (node != null) {
                    node.removeAttributeObserver(observer);
                }
            }
        }
        subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

    private void initializeNodes() {
        ensureVariablesFolder();
    }

    private static String variantToString(Variant variant) {
        if (variant == null || variant.getValue() == null) {
            return "";
        }
        return variant.getValue().toString();
    }

    private void ensureVariablesFolder() {
        if (variablesFolder != null) {
            return;
        }
        variablesFolder = new UaFolderNode(
                getNodeContext(),
                newNodeId("IspfVariables"),
                new QualifiedName(getNamespaceIndex(), "IspfVariables"),
                LocalizedText.english("ISPF Variables")
        );
        getNodeManager().addNode(variablesFolder);
        variablesFolder.addReference(new Reference(
                variablesFolder.getNodeId(),
                Identifiers.Organizes,
                Identifiers.ObjectsFolder.expanded(),
                false
        ));
    }
}
