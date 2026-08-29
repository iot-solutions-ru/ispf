package com.ispf.driver.protocolstubs;

/**
 * PROFIBUS PA protocol stub (profibus-pa).
 * <p>
 * PROFIBUS PA instrument network stub.
 */
public class ProfibusPaDeviceDriver extends ProtocolStubDeviceDriver {

    public ProfibusPaDeviceDriver() {
        super(
                "profibus-pa",
                "PROFIBUS PA Driver",
                "PROFIBUS PA instrument network stub",
                9600
        );
    }
}
