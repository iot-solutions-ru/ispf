package com.ispf.driver.gesrtp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * GE SRTP protocol stub (ge-srtp).
 * <p>
 * Emerson/GE Fanuc SRTP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class GeSrtpDeviceDriver extends ProtocolStubDeviceDriver {

    public GeSrtpDeviceDriver() {
        super(
                "ge-srtp",
                "GE SRTP Driver",
                "Emerson/GE Fanuc SRTP stub",
                18245
        );
    }
}
