package com.ispf.driver.protocolstubs;

/**
 * SECS/GEM protocol stub (secs-gem).
 * <p>
 * SEMI SECS-I/HSMS/GEM stub.
 */
public class SecsGemDeviceDriver extends ProtocolStubDeviceDriver {

    public SecsGemDeviceDriver() {
        super(
                "secs-gem",
                "SECS/GEM Driver",
                "SEMI SECS-I/HSMS/GEM stub",
                5000
        );
    }
}
