package com.ispf.driver.protocolstubs;

/**
 * Beckhoff ADS protocol stub (beckhoff-ads).
 * <p>
 * Beckhoff TwinCAT ADS/AMS stub.
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
