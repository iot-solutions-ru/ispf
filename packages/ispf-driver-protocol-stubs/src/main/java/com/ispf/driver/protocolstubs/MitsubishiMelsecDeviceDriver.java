package com.ispf.driver.protocolstubs;

/**
 * Mitsubishi MELSEC protocol stub (mitsubishi-melsec).
 * <p>
 * Mitsubishi MELSEC communication stub (MC Protocol / SLMP path planned).
 */
public class MitsubishiMelsecDeviceDriver extends ProtocolStubDeviceDriver {

    public MitsubishiMelsecDeviceDriver() {
        super(
                "mitsubishi-melsec",
                "Mitsubishi MELSEC Driver",
                "Mitsubishi MELSEC communication stub (MC Protocol / SLMP path planned)",
                5007
        );
    }
}
