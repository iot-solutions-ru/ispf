package com.ispf.driver.protocolstubs;

/**
 * DALI protocol stub (dali).
 * <p>
 * DALI lighting gateway stub.
 */
public class DaliDeviceDriver extends ProtocolStubDeviceDriver {

    public DaliDeviceDriver() {
        super(
                "dali",
                "DALI Driver",
                "DALI lighting gateway stub",
                4001
        );
    }
}
