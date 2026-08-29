package com.ispf.driver.protocolstubs;

/**
 * Toshiba T-series protocol stub (toshiba-t-series).
 * <p>
 * Toshiba T-series PLC stub.
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
