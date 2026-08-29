package com.ispf.driver.protocolstubs;

/**
 * Fanuc FOCAS protocol stub (fanuc-focas).
 * <p>
 * Fanuc FOCAS CNC stub.
 */
public class FanucFocasDeviceDriver extends ProtocolStubDeviceDriver {

    public FanucFocasDeviceDriver() {
        super(
                "fanuc-focas",
                "Fanuc FOCAS Driver",
                "Fanuc FOCAS CNC stub",
                8193
        );
    }
}
