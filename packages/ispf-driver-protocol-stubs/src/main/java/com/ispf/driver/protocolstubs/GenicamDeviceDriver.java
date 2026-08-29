package com.ispf.driver.protocolstubs;

/**
 * GenICam protocol stub (genicam).
 * <p>
 * GenICam / GigE Vision stub.
 */
public class GenicamDeviceDriver extends ProtocolStubDeviceDriver {

    public GenicamDeviceDriver() {
        super(
                "genicam",
                "GenICam Driver",
                "GenICam / GigE Vision stub",
                3956
        );
    }
}
