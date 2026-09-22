package com.ispf.driver.enocean.codec;

import java.util.Arrays;

/**
 * Decoded ESP3 packet (type, data, optional). Payload octets are cloned — not a {@code byte[]} record.
 */
public final class Esp3Packet {

    private final int packetType;
    private final byte[] data;
    private final byte[] optional;

    public Esp3Packet(int packetType, byte[] data, byte[] optional) {
        this.packetType = packetType & 0xFF;
        this.data = data == null ? new byte[0] : data.clone();
        this.optional = optional == null ? new byte[0] : optional.clone();
    }

    public int packetType() {
        return packetType;
    }

    public byte[] data() {
        return data.clone();
    }

    public byte[] optional() {
        return optional.clone();
    }

    public byte[] deviceId() {
        if (data.length < 4) {
            throw new IllegalArgumentException("ESP3 RADIO data missing device id");
        }
        return Arrays.copyOfRange(data, 0, 4);
    }

    public byte[] payload() {
        if (data.length <= 4) {
            return new byte[0];
        }
        return Arrays.copyOfRange(data, 4, data.length);
    }
}
