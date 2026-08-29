package com.ispf.driver.protocolstubs;

/**
 * VISA protocol stub (visa).
 * <p>
 * IVI/VISA instrument resource stub.
 */
public class VisaDeviceDriver extends ProtocolStubDeviceDriver {

    public VisaDeviceDriver() {
        super(
                "visa",
                "VISA Driver",
                "IVI/VISA instrument resource stub",
                5025
        );
    }
}
