package com.ispf.driver.protocolstubs;

/**
 * Keyence Host Link protocol stub (keyence-hostlink).
 * <p>
 * Keyence PLC Host Link / KV stub.
 */
public class KeyenceHostlinkDeviceDriver extends ProtocolStubDeviceDriver {

    public KeyenceHostlinkDeviceDriver() {
        super(
                "keyence-hostlink",
                "Keyence Host Link Driver",
                "Keyence PLC Host Link / KV stub",
                8501
        );
    }
}
