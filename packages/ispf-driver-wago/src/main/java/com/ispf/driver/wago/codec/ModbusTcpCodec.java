package com.ispf.driver.wago.codec;

import java.nio.ByteBuffer;

/**
 * Modbus TCP (MBAP) request helpers — transaction id, protocol id 0, length, unit id, then PDU.
 */
public final class ModbusTcpCodec {

    public static final byte FC_READ_HOLDING = 3;
    public static final byte FC_WRITE_SINGLE = 6;

    private ModbusTcpCodec() {
    }

    public static byte[] encodeMbap(int transactionId, int unitId, byte[] pdu) {
        ByteBuffer frame = ByteBuffer.allocate(7 + pdu.length);
        frame.putShort((short) (transactionId & 0xFFFF));
        frame.putShort((short) 0);
        frame.putShort((short) (1 + pdu.length));
        frame.put((byte) (unitId & 0xFF));
        frame.put(pdu);
        return frame.array();
    }

    public static byte[] encodeReadHoldingRegisters(int transactionId, int unitId, int address, int quantity) {
        ByteBuffer pdu = ByteBuffer.allocate(5);
        pdu.put(FC_READ_HOLDING);
        pdu.putShort((short) (address & 0xFFFF));
        pdu.putShort((short) (quantity & 0xFFFF));
        return encodeMbap(transactionId, unitId, pdu.array());
    }

    public static byte[] encodeWriteSingleRegister(int transactionId, int unitId, int address, int value) {
        ByteBuffer pdu = ByteBuffer.allocate(5);
        pdu.put(FC_WRITE_SINGLE);
        pdu.putShort((short) (address & 0xFFFF));
        pdu.putShort((short) (value & 0xFFFF));
        return encodeMbap(transactionId, unitId, pdu.array());
    }

    /**
     * Reference FC3: transaction id 1, protocol 0, length 6, unit 1, address 0, quantity 1
     * → {@code 00 01 00 00 00 06 01 03 00 00 00 01}.
     */
    public static byte[] buildReadHoldingRegister0Reference() {
        return encodeReadHoldingRegisters(1, 1, 0, 1);
    }
}
