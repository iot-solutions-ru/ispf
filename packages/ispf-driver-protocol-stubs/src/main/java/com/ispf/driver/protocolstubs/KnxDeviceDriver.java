package com.ispf.driver.protocolstubs;

/**
 * KNX/IP protocol stub (knx).
 * <p>
 * KNX/IP tunneling/routing stub.
 */
public class KnxDeviceDriver extends ProtocolStubDeviceDriver {

    public KnxDeviceDriver() {
        super(
                "knx",
                "KNX/IP Driver",
                "KNX/IP tunneling/routing stub",
                3671
        );
    }
}
