package com.ispf.driver.protocolstubs;

/**
 * SAE J1939 protocol stub (j1939).
 * <p>
 * SAE J1939 vehicle network stub.
 */
public class J1939DeviceDriver extends ProtocolStubDeviceDriver {

    public J1939DeviceDriver() {
        super(
                "j1939",
                "SAE J1939 Driver",
                "SAE J1939 vehicle network stub",
                29536
        );
    }
}
