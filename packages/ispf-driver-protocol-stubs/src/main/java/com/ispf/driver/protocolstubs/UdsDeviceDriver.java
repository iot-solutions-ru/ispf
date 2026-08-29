package com.ispf.driver.protocolstubs;

/**
 * UDS (ISO 14229) protocol stub (uds).
 * <p>
 * Unified Diagnostic Services over DoIP stub.
 */
public class UdsDeviceDriver extends ProtocolStubDeviceDriver {

    public UdsDeviceDriver() {
        super(
                "uds",
                "UDS (ISO 14229) Driver",
                "Unified Diagnostic Services over DoIP stub",
                13400
        );
    }
}
