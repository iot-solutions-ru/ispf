package com.ispf.driver.protocolstubs;

/**
 * IEC 61850 Sampled Values protocol stub (iec61850-sv).
 * <p>
 * IEC 61850 Sampled Values stub.
 */
public class Iec61850SvDeviceDriver extends ProtocolStubDeviceDriver {

    public Iec61850SvDeviceDriver() {
        super(
                "iec61850-sv",
                "IEC 61850 Sampled Values Driver",
                "IEC 61850 Sampled Values stub",
                102
        );
    }
}
