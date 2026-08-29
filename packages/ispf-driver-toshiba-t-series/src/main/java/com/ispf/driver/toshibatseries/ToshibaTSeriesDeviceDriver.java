package com.ispf.driver.toshibatseries;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Toshiba T-series protocol stub (toshiba-t-series).
 * <p>
 * Toshiba T-series PLC stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class ToshibaTSeriesDeviceDriver extends ProtocolStubDeviceDriver {

    public ToshibaTSeriesDeviceDriver() {
        super(
                "toshiba-t-series",
                "Toshiba T-series Driver",
                "Toshiba T-series PLC stub",
                9600
        );
    }
}
