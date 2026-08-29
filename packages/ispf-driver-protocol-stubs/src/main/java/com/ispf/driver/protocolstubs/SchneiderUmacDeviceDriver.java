package com.ispf.driver.protocolstubs;

/**
 * Schneider Unity/Modicon protocol stub (schneider-umac).
 * <p>
 * Schneider Electric Unity/Modicon advanced services stub (beyond Modbus).
 */
public class SchneiderUmacDeviceDriver extends ProtocolStubDeviceDriver {

    public SchneiderUmacDeviceDriver() {
        super(
                "schneider-umac",
                "Schneider Unity/Modicon Driver",
                "Schneider Electric Unity/Modicon advanced services stub (beyond Modbus)",
                502
        );
    }
}
