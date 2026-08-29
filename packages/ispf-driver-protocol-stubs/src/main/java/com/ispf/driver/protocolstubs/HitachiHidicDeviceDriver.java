package com.ispf.driver.protocolstubs;

/**
 * Hitachi HIDIC protocol stub (hitachi-hidic).
 * <p>
 * Hitachi HIDIC / EH-150 stub.
 */
public class HitachiHidicDeviceDriver extends ProtocolStubDeviceDriver {

    public HitachiHidicDeviceDriver() {
        super(
                "hitachi-hidic",
                "Hitachi HIDIC Driver",
                "Hitachi HIDIC / EH-150 stub",
                3000
        );
    }
}
