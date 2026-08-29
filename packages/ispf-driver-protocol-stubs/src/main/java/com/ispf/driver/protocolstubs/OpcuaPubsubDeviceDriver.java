package com.ispf.driver.protocolstubs;

/**
 * OPC UA PubSub protocol stub (opcua-pubsub).
 * <p>
 * OPC UA PubSub (UDP/MQTT) stub — connectivity shell only.
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
