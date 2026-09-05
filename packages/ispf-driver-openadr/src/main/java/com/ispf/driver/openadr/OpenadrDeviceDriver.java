package com.ispf.driver.openadr;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * OpenADR protocol stub (openadr).
 * <p>
 * OpenADR 2.0b VTN/VEN stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class OpenadrDeviceDriver extends ProtocolStubDeviceDriver {

    public OpenadrDeviceDriver() {
        super(
                "openadr",
                "OpenADR Driver",
                "OpenADR 2.0b VTN/VEN stub",
                443
        );
    }
}
