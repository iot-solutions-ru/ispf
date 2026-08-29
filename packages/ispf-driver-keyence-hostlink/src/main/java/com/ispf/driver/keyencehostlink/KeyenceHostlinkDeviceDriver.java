package com.ispf.driver.keyencehostlink;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Keyence Host Link protocol stub (keyence-hostlink).
 * <p>
 * Keyence PLC Host Link / KV stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
