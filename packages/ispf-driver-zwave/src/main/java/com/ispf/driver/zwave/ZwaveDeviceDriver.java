package com.ispf.driver.zwave;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Z-Wave protocol stub (zwave).
 * <p>
 * Z-Wave controller stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ZwaveDeviceDriver extends ProtocolStubDeviceDriver {

    public ZwaveDeviceDriver() {
        super(
                "zwave",
                "Z-Wave Driver",
                "Z-Wave controller stub",
                3000
        );
    }
}
