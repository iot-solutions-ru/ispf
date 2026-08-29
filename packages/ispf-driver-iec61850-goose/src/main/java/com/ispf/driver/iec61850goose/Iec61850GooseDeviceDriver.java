package com.ispf.driver.iec61850goose;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IEC 61850 GOOSE protocol stub (iec61850-goose).
 * <p>
 * IEC 61850 GOOSE subscriber stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class Iec61850GooseDeviceDriver extends ProtocolStubDeviceDriver {

    public Iec61850GooseDeviceDriver() {
        super(
                "iec61850-goose",
                "IEC 61850 GOOSE Driver",
                "IEC 61850 GOOSE subscriber stub",
                102
        );
    }
}
