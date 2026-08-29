package com.ispf.driver.lwm2m;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * LwM2M protocol stub (lwm2m).
 * <p>
 * OMA LwM2M client/server stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class Lwm2mDeviceDriver extends ProtocolStubDeviceDriver {

    public Lwm2mDeviceDriver() {
        super(
                "lwm2m",
                "LwM2M Driver",
                "OMA LwM2M client/server stub",
                5683
        );
    }
}
