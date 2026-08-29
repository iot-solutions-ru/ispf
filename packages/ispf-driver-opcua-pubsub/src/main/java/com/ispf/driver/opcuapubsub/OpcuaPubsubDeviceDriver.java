package com.ispf.driver.opcuapubsub;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * OPC UA PubSub protocol stub (opcua-pubsub).
 * <p>
 * OPC UA PubSub (UDP/MQTT) stub — connectivity shell only.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class OpcuaPubsubDeviceDriver extends ProtocolStubDeviceDriver {

    public OpcuaPubsubDeviceDriver() {
        super(
                "opcua-pubsub",
                "OPC UA PubSub Driver",
                "OPC UA PubSub (UDP/MQTT) stub — connectivity shell only",
                4840
        );
    }
}
