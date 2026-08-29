package com.ispf.driver.protocolstubs;

/**
 * LwM2M protocol stub (lwm2m).
 * <p>
 * OMA LwM2M client/server stub.
 */
public class Lwm2mDeviceDriver extends ProtocolStubDeviceDriver {

    public Lwm2mDeviceDriver() {
        super(
                "lwm2m",
                "LwM2M Driver",
                "OMA LwM2M client/server stub",
                5683
        );
    }
}
