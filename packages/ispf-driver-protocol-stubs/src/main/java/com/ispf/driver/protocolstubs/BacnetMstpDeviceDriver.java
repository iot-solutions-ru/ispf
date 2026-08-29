package com.ispf.driver.protocolstubs;

/**
 * BACnet MS/TP protocol stub (bacnet-mstp).
 * <p>
 * BACnet MS/TP serial stub (BACnet/IP pack is separate).
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
