package com.ispf.driver.genicam;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * GenICam protocol stub (genicam).
 * <p>
 * GenICam / GigE Vision stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class GenicamDeviceDriver extends ProtocolStubDeviceDriver {

    public GenicamDeviceDriver() {
        super(
                "genicam",
                "GenICam Driver",
                "GenICam / GigE Vision stub",
                3956
        );
    }
}
