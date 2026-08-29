package com.ispf.driver.knxtp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * KNX TP protocol stub (knx-tp).
 * <p>
 * KNX Twisted Pair interface stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class KnxTpDeviceDriver extends ProtocolStubDeviceDriver {

    public KnxTpDeviceDriver() {
        super(
                "knx-tp",
                "KNX TP Driver",
                "KNX Twisted Pair interface stub",
                3671
        );
    }
}
