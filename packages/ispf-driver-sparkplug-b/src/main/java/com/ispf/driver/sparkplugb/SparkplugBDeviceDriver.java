package com.ispf.driver.sparkplugb;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * MQTT Sparkplug B protocol stub (sparkplug-b).
 * <p>
 * MQTT Sparkplug B host/edge stub (MQTT session + Sparkplug payload parsing not implemented).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class SparkplugBDeviceDriver extends ProtocolStubDeviceDriver {

    public SparkplugBDeviceDriver() {
        super(
                "sparkplug-b",
                "MQTT Sparkplug B Driver",
                "MQTT Sparkplug B host/edge stub (MQTT session + Sparkplug payload parsing not implemented)",
                1883
        );
    }
}
