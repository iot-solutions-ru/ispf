package com.ispf.driver.protocolstubs;

/**
 * PROFIBUS protocol stub (profibus).
 * <p>
 * PROFIBUS DP/PA gateway stub (serial/fieldbus bridge required).
 */
public class ProfibusDeviceDriver extends ProtocolStubDeviceDriver {

    public ProfibusDeviceDriver() {
        super(
                "profibus",
                "PROFIBUS Driver",
                "PROFIBUS DP/PA gateway stub (serial/fieldbus bridge required)",
                9600
        );
    }
}
