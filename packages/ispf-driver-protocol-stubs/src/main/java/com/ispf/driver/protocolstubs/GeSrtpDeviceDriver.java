package com.ispf.driver.protocolstubs;

/**
 * GE SRTP protocol stub (ge-srtp).
 * <p>
 * Emerson/GE Fanuc SRTP stub.
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
