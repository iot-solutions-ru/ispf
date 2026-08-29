package com.ispf.driver.hartip;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * HART-IP protocol stub (hart-ip).
 * <p>
 * HART-IP (UDP/TCP) stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class HartIpDeviceDriver extends ProtocolStubDeviceDriver {

    public HartIpDeviceDriver() {
        super(
                "hart-ip",
                "HART-IP Driver",
                "HART-IP (UDP/TCP) stub",
                5094
        );
    }
}
