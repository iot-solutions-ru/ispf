package com.ispf.driver.protocolstubs;

/**
 * PROFINET IO protocol stub (profinet).
 * <p>
 * PROFINET IO controller/device stub (DCP/RPC not implemented).
 */
public class ProfinetDeviceDriver extends ProtocolStubDeviceDriver {

    public ProfinetDeviceDriver() {
        super(
                "profinet",
                "PROFINET IO Driver",
                "PROFINET IO controller/device stub (DCP/RPC not implemented)",
                34964
        );
    }
}
