package com.ispf.driver.knx;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * KNX/IP protocol stub (knx).
 * <p>
 * KNX/IP tunneling/routing stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class KnxDeviceDriver extends ProtocolStubDeviceDriver {

    public KnxDeviceDriver() {
        super(
                "knx",
                "KNX/IP Driver",
                "KNX/IP tunneling/routing stub",
                3671
        );
    }
}
