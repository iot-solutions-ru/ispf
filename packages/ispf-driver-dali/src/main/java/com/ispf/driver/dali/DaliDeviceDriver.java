package com.ispf.driver.dali;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * DALI protocol stub (dali).
 * <p>
 * DALI lighting gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class DaliDeviceDriver extends ProtocolStubDeviceDriver {

    public DaliDeviceDriver() {
        super(
                "dali",
                "DALI Driver",
                "DALI lighting gateway stub",
                4001
        );
    }
}
