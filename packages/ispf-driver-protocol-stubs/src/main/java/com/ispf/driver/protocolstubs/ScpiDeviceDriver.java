package com.ispf.driver.protocolstubs;

/**
 * SCPI protocol stub (scpi).
 * <p>
 * IEEE 488.2 SCPI instrument stub.
 */
public class ScpiDeviceDriver extends ProtocolStubDeviceDriver {

    public ScpiDeviceDriver() {
        super(
                "scpi",
                "SCPI Driver",
                "IEEE 488.2 SCPI instrument stub",
                5025
        );
    }
}
