package com.ispf.driver.bacnetmstp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * BACnet MS/TP protocol stub (bacnet-mstp).
 * <p>
 * BACnet MS/TP serial stub (BACnet/IP pack is separate).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class BacnetMstpDeviceDriver extends ProtocolStubDeviceDriver {

    public BacnetMstpDeviceDriver() {
        super(
                "bacnet-mstp",
                "BACnet MS/TP Driver",
                "BACnet MS/TP serial stub (BACnet/IP pack is separate)",
                47808
        );
    }
}
