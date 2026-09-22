package com.ispf.driver.bluetoothle;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bluetooth H4 HCI point mapping.
 * <p>
 * Form: {@code bd_addr} or {@code hci:bd_addr} — reads the controller address via
 * HCI_Read_BD_ADDR. This driver is not a full GATT client.
 */
record BluetoothLePoint(Kind kind, String display) {

    enum Kind {
        BD_ADDR
    }

    private static final Pattern BD_ADDR = Pattern.compile(
            "^(?:hci\\s*[:=]\\s*)?bd[_-]?addr$",
            Pattern.CASE_INSENSITIVE);

    static BluetoothLePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Bluetooth LE point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher matcher = BD_ADDR.matcher(trimmed);
        if (matcher.matches()) {
            return new BluetoothLePoint(Kind.BD_ADDR, "bd_addr");
        }
        throw new DriverException(
                "Unsupported Bluetooth H4 HCI mapping (expected bd_addr or hci:bd_addr): "
                        + mapping);
    }

    boolean writable() {
        return false;
    }

    String wireToken() {
        return display.toLowerCase(Locale.ROOT);
    }
}
