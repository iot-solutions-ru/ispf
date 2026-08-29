package com.ispf.driver.mqttsn;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * MQTT-SN protocol stub (mqtt-sn).
 * <p>
 * MQTT For Sensor Networks stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
