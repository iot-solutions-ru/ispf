package com.ispf.driver.opcua;

import com.ispf.driver.DriverException;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Parsed OPC UA point reference from mapping string {@code nodeId}.
 * Examples: {@code ns=2;s=Temperature}, {@code i=2258}, {@code ns=0;i=2258}.
 */
record OpcUaPoint(NodeId nodeId) {

    static OpcUaPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("OPC UA mapping requires nodeId: " + mapping);
        }
        try {
            return new OpcUaPoint(NodeId.parse(mapping.trim()));
        } catch (Exception e) {
            throw new DriverException("Invalid OPC UA nodeId: " + mapping, e);
        }
    }
}
