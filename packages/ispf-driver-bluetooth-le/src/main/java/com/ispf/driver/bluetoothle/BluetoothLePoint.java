package com.ispf.driver.bluetoothle;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bluetooth LE GATT gateway lab point.
 * <p>
 * Forms: {@code mac:AA:BB:CC:DD:EE:FF:svc:180f:char:2a19}, {@code device:1:rssi}.
 */
record BluetoothLePoint(Kind kind, String display, String mac, String service, String characteristic,
                        int deviceIndex) {

    enum Kind {
        GATT_CHAR,
        DEVICE_RSSI
    }

    private static final Pattern GATT = Pattern.compile(
            "^mac\\s*[:=]\\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})"
                    + "\\s*[:=]\\s*svc\\s*[:=]\\s*([0-9A-Fa-f]+)"
                    + "\\s*[:=]\\s*char\\s*[:=]\\s*([0-9A-Fa-f]+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RSSI = Pattern.compile(
            "^device\\s*[:=]\\s*(\\d+)\\s*[:=]\\s*rssi$",
            Pattern.CASE_INSENSITIVE);

    static BluetoothLePoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("Bluetooth LE point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher gatt = GATT.matcher(trimmed);
        if (gatt.matches()) {
            String mac = gatt.group(1).toUpperCase(Locale.ROOT);
            String svc = gatt.group(2).toLowerCase(Locale.ROOT);
            String ch = gatt.group(3).toLowerCase(Locale.ROOT);
            String display = "mac:" + mac + ":svc:" + svc + ":char:" + ch;
            return new BluetoothLePoint(Kind.GATT_CHAR, display, mac, svc, ch, -1);
        }
        Matcher rssi = RSSI.matcher(trimmed);
        if (rssi.matches()) {
            int device = Integer.parseInt(rssi.group(1));
            if (device < 1) {
                throw new DriverException("Bluetooth LE device index out of range: " + device);
            }
            String display = "device:" + device + ":rssi";
            return new BluetoothLePoint(Kind.DEVICE_RSSI, display, null, null, null, device);
        }
        throw new DriverException(
                "Unsupported Bluetooth LE mapping (expected mac:AA:BB:CC:DD:EE:FF:svc:180f:char:2a19"
                        + " or device:1:rssi): " + mapping);
    }

    boolean writable() {
        return kind == Kind.GATT_CHAR;
    }

    String wireToken() {
        return display;
    }
}
