package com.ispf.driver.opcua;

import com.ispf.driver.DriverException;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

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
        NodeId parent = parentNodeId == null || parentNodeId.isBlank()
                ? Identifiers.ObjectsFolder
                : OpcUaPoint.parse(parentNodeId).nodeId();
        OpcUaClient client = null;
        try {
            client = OpcUaClient.create(
                    endpointUrl,
                    endpoints -> endpoints.stream()
                            .filter(endpoint -> SecurityPolicy.None.getUri().equals(endpoint.getSecurityPolicyUri()))
                            .findFirst(),
                    configBuilder -> configBuilder
                            .setApplicationName(LocalizedText.english("ISPF OPC UA Browse"))
                            .setApplicationUri("urn:ispf:driver:opcua:browse")
                            .build()
            );
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
        }
    }
}
