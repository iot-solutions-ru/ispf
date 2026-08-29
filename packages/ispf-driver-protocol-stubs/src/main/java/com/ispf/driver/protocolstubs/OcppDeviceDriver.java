package com.ispf.driver.protocolstubs;

/**
 * OCPP protocol stub (ocpp).
 * <p>
 * Open Charge Point Protocol (CSMS) stub.
 */
public class OcppDeviceDriver extends ProtocolStubDeviceDriver {

    public OcppDeviceDriver() {
        super(
                "ocpp",
                "OCPP Driver",
                "Open Charge Point Protocol (CSMS) stub",
                9000
        );
    }
}
