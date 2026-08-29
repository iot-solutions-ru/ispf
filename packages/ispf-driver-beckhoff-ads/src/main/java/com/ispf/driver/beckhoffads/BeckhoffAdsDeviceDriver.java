package com.ispf.driver.beckhoffads;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Beckhoff ADS protocol stub (beckhoff-ads).
 * <p>
 * Beckhoff TwinCAT ADS/AMS stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class BeckhoffAdsDeviceDriver extends ProtocolStubDeviceDriver {

    public BeckhoffAdsDeviceDriver() {
        super(
                "beckhoff-ads",
                "Beckhoff ADS Driver",
                "Beckhoff TwinCAT ADS/AMS stub",
                48898
        );
    }
}
