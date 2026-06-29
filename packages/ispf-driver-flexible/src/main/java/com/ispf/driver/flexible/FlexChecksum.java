package com.ispf.driver.flexible;

import com.ispf.driver.DriverException;

import java.nio.charset.StandardCharsets;

/**
 * Generic checksum verification for framed ASCII/binary responses.
 */
final class FlexChecksum {

    private FlexChecksum() {
    }

    static String verifyAndPayload(byte[] frame, String algorithm, String marker, int checksumLength)
            throws DriverException {
        if (algorithm == null || algorithm.isBlank() || "none".equalsIgnoreCase(algorithm)) {
            return FlexTemplate.asPrintable(frame);
        }
        if (!"sum16-complement-hex".equalsIgnoreCase(algorithm)) {
            throw new DriverException("Unsupported checksum algorithm: " + algorithm);
        }
        if (marker == null || marker.isBlank()) {
            throw new DriverException("checksumMarker is required when checksumAlgorithm is set");
        }
        if (checksumLength <= 0) {
            throw new DriverException("checksumLength must be positive");
        }
        byte[] markerBytes = marker.getBytes(StandardCharsets.US_ASCII);
        int markerIndex = indexOf(frame, markerBytes);
        if (markerIndex < 0) {
            throw new DriverException("Checksum marker not found: " + marker);
        }
        int checksumStart = markerIndex + markerBytes.length;
        if (checksumStart + checksumLength > frame.length) {
            throw new DriverException("Checksum field truncated in response");
        }
        String checksumHex = new String(frame, checksumStart, checksumLength, StandardCharsets.US_ASCII);
        int checksumValue;
        try {
            checksumValue = Integer.parseInt(checksumHex, 16) & 0xFFFF;
        } catch (NumberFormatException e) {
            throw new DriverException("Invalid checksum hex: " + checksumHex, e);
        }
        int sum = checksumValue;
        for (int i = 0; i < markerIndex; i++) {
            sum = (sum + (frame[i] & 0xFF)) & 0xFFFF;
        }
        if (sum != 0) {
            throw new DriverException("Checksum verification failed");
        }
        return new String(frame, 0, markerIndex, StandardCharsets.US_ASCII);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
