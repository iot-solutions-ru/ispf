package com.ispf.driver.protocolstubs;

/**
 * OpenADR protocol stub (openadr).
 * <p>
 * OpenADR 2.0b VTN/VEN stub.
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
