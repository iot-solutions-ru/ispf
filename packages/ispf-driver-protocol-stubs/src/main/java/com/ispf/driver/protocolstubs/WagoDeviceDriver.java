package com.ispf.driver.protocolstubs;

/**
 * WAGO protocol stub (wago).
 * <p>
 * WAGO PFC / e!COCKPIT stub.
 */
public class WagoDeviceDriver extends ProtocolStubDeviceDriver {

    public WagoDeviceDriver() {
        super(
                "wago",
                "WAGO Driver",
                "WAGO PFC / e!COCKPIT stub",
                2455
        );
    }
}
