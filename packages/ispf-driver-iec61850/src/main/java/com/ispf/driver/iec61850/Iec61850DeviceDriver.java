package com.ispf.driver.iec61850;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IEC 61850 MMS protocol stub (iec61850).
 * <p>
 * IEC 61850 MMS client stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
