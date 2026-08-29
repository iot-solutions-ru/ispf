package com.ispf.driver.protocolstubs;

/**
 * Yaskawa Memobus protocol stub (yaskawa-memobus).
 * <p>
 * Yaskawa Memobus/Modbus-family PLC stub.
 */
public class YaskawaMemobusDeviceDriver extends ProtocolStubDeviceDriver {

    public YaskawaMemobusDeviceDriver() {
        super(
                "yaskawa-memobus",
                "Yaskawa Memobus Driver",
                "Yaskawa Memobus/Modbus-family PLC stub",
                502
        );
    }
}
