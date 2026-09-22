package com.ispf.driver.hartip.codec;

/**
 * Decoded HART-IP message: type, id, status, sequence, and body octets.
 */
public final class HartIpMessage {

    private final int messageType;
    private final int messageId;
    private final int status;
    private final int sequence;
    private final byte[] payload;

    public HartIpMessage(int messageType, int messageId, int status, int sequence, byte[] payload) {
        this.messageType = messageType;
        this.messageId = messageId;
        this.status = status;
        this.sequence = sequence & 0xFFFF;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public int messageType() {
        return messageType;
    }

    public int messageId() {
        return messageId;
    }

    public int status() {
        return status;
    }

    public int sequence() {
        return sequence;
    }

    public byte[] payload() {
        return payload.clone();
    }
}
