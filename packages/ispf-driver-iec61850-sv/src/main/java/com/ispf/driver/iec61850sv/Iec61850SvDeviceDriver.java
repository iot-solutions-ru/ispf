package com.ispf.driver.iec61850sv;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IEC 61850 Sampled Values protocol stub (iec61850-sv).
 * <p>
 * IEC 61850 Sampled Values stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class Iec61850SvDeviceDriver extends ProtocolStubDeviceDriver {

    public Iec61850SvDeviceDriver() {
        super(
                "iec61850-sv",
                "IEC 61850 Sampled Values Driver",
                "IEC 61850 Sampled Values stub",
                102
        );
    }
}
