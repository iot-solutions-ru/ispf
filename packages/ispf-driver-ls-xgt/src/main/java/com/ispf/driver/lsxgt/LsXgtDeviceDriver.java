package com.ispf.driver.lsxgt;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * LS XGT protocol stub (ls-xgt).
 * <p>
 * LS Electric XGT FEnet stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class LsXgtDeviceDriver extends ProtocolStubDeviceDriver {

    public LsXgtDeviceDriver() {
        super(
                "ls-xgt",
                "LS XGT Driver",
                "LS Electric XGT FEnet stub",
                2004
        );
    }
}
