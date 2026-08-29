package com.ispf.driver.protocolstubs;

/**
 * IEC 60870-5-103 protocol stub (iec103).
 * <p>
 * IEC 60870-5-103 protection stub.
 */
public class Iec103DeviceDriver extends ProtocolStubDeviceDriver {

    public Iec103DeviceDriver() {
        super(
                "iec103",
                "IEC 60870-5-103 Driver",
                "IEC 60870-5-103 protection stub",
                2404
        );
    }
}
