package com.ispf.driver.codesys;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * CODESYS Gateway protocol stub (codesys).
 * <p>
 * CODESYS gateway / PLCHandler stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class CodesysDeviceDriver extends ProtocolStubDeviceDriver {

    public CodesysDeviceDriver() {
        super(
                "codesys",
                "CODESYS Gateway Driver",
                "CODESYS gateway / PLCHandler stub",
                1217
        );
    }
}
