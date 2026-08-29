package com.ispf.driver.protocolstubs;

/**
 * Barcode scanner protocol stub (barcode-scanner).
 * <p>
 * Barcode/QR TCP/serial scanner stub.
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
