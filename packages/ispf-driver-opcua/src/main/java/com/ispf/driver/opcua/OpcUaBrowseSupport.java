package com.ispf.driver.opcua;

import com.ispf.driver.DriverException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ephemeral OPC UA browse helper (BL-80 discovery).
 */
public final class OpcUaBrowseSupport {

    private OpcUaBrowseSupport() {
    }

    public record BrowseNode(
            String nodeId,
            String displayName,
            String nodeClass
    ) {
    }

    public static List<BrowseNode> browseChildren(
            String endpointUrl,
            String parentNodeId,
            int timeoutMs
    ) throws DriverException {
        return browseChildren(endpointUrl, parentNodeId, timeoutMs, OpcUaSecuritySettings.none());
    }

    static List<BrowseNode> browseChildren(
            String endpointUrl,
            String parentNodeId,
            int timeoutMs,
            OpcUaSecuritySettings security
    ) throws DriverException {
        NodeId parent = parentNodeId == null || parentNodeId.isBlank()
                ? Identifiers.ObjectsFolder
                : OpcUaPoint.parse(parentNodeId).nodeId();
        OpcUaClient client = null;
        OpcUaClientPki pki = null;
        try {
            if (security.secure()) {
                if (security.pkiDir().isBlank()) {
                    throw new DriverException("pkiDir is required when securityPolicy is not None");
                }
                pki = OpcUaClientPki.loadOrCreate(
                        Path.of(security.pkiDir()),
                        "ISPF OPC UA Browse",
                        "urn:ispf:driver:opcua"
                );
            }
            client = OpcUaClients.create(endpointUrl, security, pki, "ISPF OPC UA Browse");
            client.connect().get(timeoutMs, TimeUnit.MILLISECONDS);
            List<ReferenceDescription> refs = client.getAddressSpace().browse(parent);
            List<BrowseNode> nodes = new ArrayList<>();
            for (ReferenceDescription ref : refs) {
                NodeId nodeId = ref.getNodeId().toNodeId(client.getNamespaceTable()).orElse(null);
                if (nodeId == null) {
                    continue;
                }
                String displayName = ref.getBrowseName() != null ? ref.getBrowseName().getName() : nodeId.toParseableString();
                NodeClass nodeClass = ref.getNodeClass();
                nodes.add(new BrowseNode(
                        nodeId.toParseableString(),
                        displayName,
                        nodeClass != null ? nodeClass.name() : "Unknown"
                ));
            }
            return nodes;
        } catch (DriverException e) {
            throw e;
        } catch (Exception e) {
            throw new DriverException("OPC UA browse failed", e);
        } finally {
            if (client != null) {
                try {
                    client.disconnect().get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    // best effort
                }
            }
            if (pki != null) {
                pki.close();
            }
        }
    }
}
