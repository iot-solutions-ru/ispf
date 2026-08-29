package com.ispf.driver.barcodescanner;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Barcode scanner protocol stub (barcode-scanner).
 * <p>
 * Barcode/QR TCP/serial scanner stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class BarcodeScannerDeviceDriver extends ProtocolStubDeviceDriver {

    public BarcodeScannerDeviceDriver() {
        super(
                "barcode-scanner",
                "Barcode scanner Driver",
                "Barcode/QR TCP/serial scanner stub",
                9001
        );
    }
}
