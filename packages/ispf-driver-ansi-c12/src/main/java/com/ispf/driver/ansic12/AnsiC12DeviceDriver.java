package com.ispf.driver.ansic12;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * ANSI C12 protocol stub (ansi-c12).
 * <p>
 * ANSI C12.18/C12.22 meter stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class AnsiC12DeviceDriver extends ProtocolStubDeviceDriver {

    public AnsiC12DeviceDriver() {
        super(
                "ansi-c12",
                "ANSI C12 Driver",
                "ANSI C12.18/C12.22 meter stub",
                1153
        );
    }
}
