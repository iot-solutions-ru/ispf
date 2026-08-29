package com.ispf.driver.iec62056;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * IEC 62056 protocol stub (iec62056).
 * <p>
 * IEC 62056 DLMS companion / push stub (beyond existing DLMS pack).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
