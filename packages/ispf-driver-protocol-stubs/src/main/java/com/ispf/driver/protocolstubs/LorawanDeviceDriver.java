package com.ispf.driver.protocolstubs;

/**
 * LoRaWAN protocol stub (lorawan).
 * <p>
 * LoRaWAN network/application server gateway stub.
 */
public class LorawanDeviceDriver extends ProtocolStubDeviceDriver {

    public LorawanDeviceDriver() {
        super(
                "lorawan",
                "LoRaWAN Driver",
                "LoRaWAN network/application server gateway stub",
                1700
        );
    }
}
