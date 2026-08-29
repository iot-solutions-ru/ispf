package com.ispf.driver.yaskawamemobus;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Yaskawa Memobus protocol stub (yaskawa-memobus).
 * <p>
 * Yaskawa Memobus/Modbus-family PLC stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class YaskawaMemobusDeviceDriver extends ProtocolStubDeviceDriver {

    public YaskawaMemobusDeviceDriver() {
        super(
                "yaskawa-memobus",
                "Yaskawa Memobus Driver",
                "Yaskawa Memobus/Modbus-family PLC stub",
                502
        );
    }
}
