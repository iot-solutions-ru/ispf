package com.ispf.driver.protocolstubs;

/**
 * EnOcean protocol stub (enocean).
 * <p>
 * EnOcean ESP3 / USB gateway stub.
 */
public class EnoceanDeviceDriver extends ProtocolStubDeviceDriver {

    public EnoceanDeviceDriver() {
        super(
                "enocean",
                "EnOcean Driver",
                "EnOcean ESP3 / USB gateway stub",
                54321
        );
    }
}
