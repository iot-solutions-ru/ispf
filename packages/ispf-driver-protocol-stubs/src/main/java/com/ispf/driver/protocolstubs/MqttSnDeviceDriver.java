package com.ispf.driver.protocolstubs;

/**
 * MQTT-SN protocol stub (mqtt-sn).
 * <p>
 * MQTT For Sensor Networks stub.
 */
public class MqttSnDeviceDriver extends ProtocolStubDeviceDriver {

    public MqttSnDeviceDriver() {
        super(
                "mqtt-sn",
                "MQTT-SN Driver",
                "MQTT For Sensor Networks stub",
                1883
        );
    }
}
