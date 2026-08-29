package com.ispf.driver.secsgem;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * SECS/GEM protocol stub (secs-gem).
 * <p>
 * SEMI SECS-I/HSMS/GEM stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class SecsGemDeviceDriver extends ProtocolStubDeviceDriver {

    public SecsGemDeviceDriver() {
        super(
                "secs-gem",
                "SECS/GEM Driver",
                "SEMI SECS-I/HSMS/GEM stub",
                5000
        );
    }
}
