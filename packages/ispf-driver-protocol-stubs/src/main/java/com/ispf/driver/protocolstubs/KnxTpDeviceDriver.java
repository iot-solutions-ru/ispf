package com.ispf.driver.protocolstubs;

/**
 * KNX TP protocol stub (knx-tp).
 * <p>
 * KNX Twisted Pair interface stub.
 */
public class KnxTpDeviceDriver extends ProtocolStubDeviceDriver {

    public KnxTpDeviceDriver() {
        super(
                "knx-tp",
                "KNX TP Driver",
                "KNX Twisted Pair interface stub",
                3671
        );
    }
}
