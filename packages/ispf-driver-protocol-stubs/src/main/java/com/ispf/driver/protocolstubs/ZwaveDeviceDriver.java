package com.ispf.driver.protocolstubs;

/**
 * Z-Wave protocol stub (zwave).
 * <p>
 * Z-Wave controller stub.
 */
public class ZwaveDeviceDriver extends ProtocolStubDeviceDriver {

    public ZwaveDeviceDriver() {
        super(
                "zwave",
                "Z-Wave Driver",
                "Z-Wave controller stub",
                3000
        );
    }
}
