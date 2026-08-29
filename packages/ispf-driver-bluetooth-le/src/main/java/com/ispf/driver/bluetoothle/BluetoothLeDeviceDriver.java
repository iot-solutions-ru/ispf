package com.ispf.driver.bluetoothle;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Bluetooth LE protocol stub (bluetooth-le).
 * <p>
 * Bluetooth Low Energy gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class BluetoothLeDeviceDriver extends ProtocolStubDeviceDriver {

    public BluetoothLeDeviceDriver() {
        super(
                "bluetooth-le",
                "Bluetooth LE Driver",
                "Bluetooth Low Energy gateway stub",
                9999
        );
    }
}
