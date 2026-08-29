package com.ispf.driver.protocolstubs;

/**
 * Rockwell CSP protocol stub (rockwell-csp).
 * <p>
 * Allen-Bradley CSP (legacy Ethernet) stub.
 */
public class RockwellCspDeviceDriver extends ProtocolStubDeviceDriver {

    public RockwellCspDeviceDriver() {
        super(
                "rockwell-csp",
                "Rockwell CSP Driver",
                "Allen-Bradley CSP (legacy Ethernet) stub",
                2222
        );
    }
}
