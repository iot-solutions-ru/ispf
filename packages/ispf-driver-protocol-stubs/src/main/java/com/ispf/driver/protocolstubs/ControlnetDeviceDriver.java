package com.ispf.driver.protocolstubs;

/**
 * ControlNet protocol stub (controlnet).
 * <p>
 * ODVA ControlNet gateway stub.
 */
public class ControlnetDeviceDriver extends ProtocolStubDeviceDriver {

    public ControlnetDeviceDriver() {
        super(
                "controlnet",
                "ControlNet Driver",
                "ODVA ControlNet gateway stub",
                2222
        );
    }
}
