package com.ispf.driver.opcuaserver;

import com.ispf.driver.DriverException;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom OPC UA namespace backing ISPF server variable points.
 */
final class IspfOpcUaNamespace extends ManagedNamespaceWithLifecycle {

    private static final String NAMESPACE_URI = "urn:ispf:opcua:server";

    private final Map<NodeId, UaVariableNode> variableNodes = new ConcurrentHashMap<>();
    private final Map<NodeId, String> variableValues = new ConcurrentHashMap<>();
    private UaFolderNode variablesFolder;

    IspfOpcUaNamespace(OpcUaServer server) {
        super(server, NAMESPACE_URI);
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
            getNodeManager().addNode(variableNode);
            variablesFolder.addOrganizes(variableNode);
            variableNodes.put(point.nodeId(), variableNode);
        } catch (Exception e) {
            throw new DriverException("Failed to create OPC UA variable node " + point.nodeId(), e);
        }
    }

    void writeValue(NodeId nodeId, String value) {
        variableValues.put(nodeId, value);
        UaVariableNode node = variableNodes.get(nodeId);
        if (node != null) {
            node.setValue(new DataValue(new Variant(value)));
        }
    }

    String readValue(NodeId nodeId) {
        return variableValues.getOrDefault(nodeId, "");
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        // no-op for v0.1
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        // no-op for v0.1
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        // no-op for v0.1
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        // no-op for v0.1
    }

    private void initializeNodes() {
        ensureVariablesFolder();
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
