package com.ispf.driver.protocolstubs;

/**
 * MQTT Sparkplug B protocol stub (sparkplug-b).
 * <p>
 * MQTT Sparkplug B host/edge stub (MQTT session + Sparkplug payload parsing not implemented).
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
