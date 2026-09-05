package com.ispf.driver.lorawan.codec;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LoRaWAN NS/AS JSON lab dialect over TCP (packet-forwarder–shaped JSON fields).
 * <p>
 * Not a LoRa PHY / Semtech HAL. Clean-room Apache-2.0, JDK only.
 */
public final class LorawanLabCodec {

    private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern RSSI = Pattern.compile("\"rssi\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern DEVEUI = Pattern.compile("\"deveui\"\\s*:\\s*\"([^\"]+)\"");

    private LorawanLabCodec() {
    }

    public static byte[] encodeGet(String deveui) {
        return ("GET " + deveui + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] encodeTx(String deveui, float value) {
        return ("TX " + deveui + " " + Float.toString(value) + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static Uplink parseUplink(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty LoRaWAN lab response");
        }
        String trimmed = line.trim();
        if (trimmed.regionMatches(true, 0, "ERR", 0, 3)) {
            throw new IllegalArgumentException("LoRaWAN lab error: " + trimmed);
        }
        Matcher valueMatch = VALUE.matcher(trimmed);
        if (!valueMatch.find()) {
            throw new IllegalArgumentException("LoRaWAN lab response missing value: " + trimmed);
        }
        float value = Float.parseFloat(valueMatch.group(1));
        double rssi = -90.0;
        Matcher rssiMatch = RSSI.matcher(trimmed);
        if (rssiMatch.find()) {
            rssi = Double.parseDouble(rssiMatch.group(1));
        }
        String deveui = "";
        Matcher deveuiMatch = DEVEUI.matcher(trimmed);
        if (deveuiMatch.find()) {
            deveui = deveuiMatch.group(1).toUpperCase(Locale.ROOT);
        }
        return new Uplink(value, rssi, deveui, trimmed);
    }

    public static void parseOkAck(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Empty LoRaWAN lab write ack");
        }
        String trimmed = line.trim();
        if (trimmed.regionMatches(true, 0, "ERR", 0, 3)) {
            throw new IllegalArgumentException("LoRaWAN lab write error: " + trimmed);
        }
        if (!(trimmed.contains("\"ok\":true") || trimmed.regionMatches(true, 0, "OK", 0, 2))) {
            throw new IllegalArgumentException("LoRaWAN lab unexpected write ack: " + trimmed);
        }
    }

    public record Uplink(float value, double rssi, String deveui, String raw) {
    }
}
