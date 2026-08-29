package com.ispf.driver.protocolstubs;

/**
 * IEC 62056 protocol stub (iec62056).
 * <p>
 * IEC 62056 DLMS companion / push stub (beyond existing DLMS pack).
 */
public class Iec62056DeviceDriver extends ProtocolStubDeviceDriver {

    public Iec62056DeviceDriver() {
        super(
                "iec62056",
                "IEC 62056 Driver",
                "IEC 62056 DLMS companion / push stub (beyond existing DLMS pack)",
                4059
        );
    }
}
