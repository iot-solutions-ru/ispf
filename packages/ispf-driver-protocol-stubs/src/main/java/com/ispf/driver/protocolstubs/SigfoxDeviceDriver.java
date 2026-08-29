package com.ispf.driver.protocolstubs;

/**
 * Sigfox protocol stub (sigfox).
 * <p>
 * Sigfox backend callback stub.
 */
public class SigfoxDeviceDriver extends ProtocolStubDeviceDriver {

    public SigfoxDeviceDriver() {
        super(
                "sigfox",
                "Sigfox Driver",
                "Sigfox backend callback stub",
                443
        );
    }
}
