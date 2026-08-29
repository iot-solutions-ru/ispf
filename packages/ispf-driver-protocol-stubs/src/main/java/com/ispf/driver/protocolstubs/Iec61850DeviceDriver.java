package com.ispf.driver.protocolstubs;

/**
 * IEC 61850 MMS protocol stub (iec61850).
 * <p>
 * IEC 61850 MMS client stub.
 */
public class Iec61850DeviceDriver extends ProtocolStubDeviceDriver {

    public Iec61850DeviceDriver() {
        super(
                "iec61850",
                "IEC 61850 MMS Driver",
                "IEC 61850 MMS client stub",
                102
        );
    }
}
