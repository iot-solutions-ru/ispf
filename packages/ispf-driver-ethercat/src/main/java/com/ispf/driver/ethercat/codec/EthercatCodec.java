package com.ispf.driver.ethercat.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * EtherCAT datagram codec (ETG.1000 layout) for serial/raw TCP gateways.
 * <p>
 * Datagram: cmd, idx, ADP, ADO, length-and-flags, IRQ, data, working counter.
 * Length field: low 11 bits = data length; bit 15 = more-following.
 */
public final class EthercatCodec {

    public static final int CMD_LRD = 0x0A;
    public static final int CMD_LWR = 0x0B;
    public static final int CMD_LRW = 0x0C;

    private EthercatCodec() {
    }

    public static byte[] buildDatagram(
            int cmd,
            int idx,
            int adp,
            int ado,
            boolean moreFollowing,
            int irq,
            int[] dataBytes,
            int workingCounter
    ) {
        int dataLen = dataBytes == null ? 0 : dataBytes.length;
        if (dataLen > 0x7FF) {
            throw new IllegalArgumentException("EtherCAT data length exceeds 11 bits");
        }
        int lengthField = dataLen & 0x7FF;
        if (moreFollowing) {
            lengthField |= 0x8000;
        }
        ByteBuffer buf = ByteBuffer.allocate(12 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) (cmd & 0xFF));
        buf.put((byte) (idx & 0xFF));
        buf.putShort((short) (adp & 0xFFFF));
        buf.putShort((short) (ado & 0xFFFF));
        buf.putShort((short) lengthField);
        buf.putShort((short) (irq & 0xFFFF));
        if (dataBytes != null) {
            for (int value : dataBytes) {
                buf.put((byte) (value & 0xFF));
            }
        }
        buf.putShort((short) (workingCounter & 0xFFFF));
        return buf.array();
    }

    /**
     * LRD, 2-byte read, idx=1, ADP=0, ADO=0x1000, IRQ=0, data zeros, WKC 0.
     * First octets: {@code 0A 01 00 00 00 10 02 00 00 00}.
     */
    public static byte[] buildLrdReference() {
        return buildDatagram(CMD_LRD, 1, 0, 0x1000, false, 0, new int[] { 0, 0 }, 0);
    }

    public static ParsedDatagram parse(byte[] frame) {
        if (frame == null || frame.length < 12) {
            throw new IllegalArgumentException("EtherCAT datagram too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = buf.get() & 0xFF;
        int idx = buf.get() & 0xFF;
        int adp = buf.getShort() & 0xFFFF;
        int ado = buf.getShort() & 0xFFFF;
        int lengthField = buf.getShort() & 0xFFFF;
        int dataLen = lengthField & 0x7FF;
        boolean more = (lengthField & 0x8000) != 0;
        int irq = buf.getShort() & 0xFFFF;
        if (frame.length < 12 + dataLen) {
            throw new IllegalArgumentException("EtherCAT datagram truncated");
        }
        int[] data = new int[dataLen];
        for (int i = 0; i < dataLen; i++) {
            data[i] = buf.get() & 0xFF;
        }
        int wkc = buf.getShort() & 0xFFFF;
        return new ParsedDatagram(cmd, idx, adp, ado, dataLen, more, irq, data, wkc);
    }

    public static int readUint16Le(int[] data) {
        if (data == null || data.length < 2) {
            return 0;
        }
        return (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
    }

    public static int[] uint16LeBytes(int value) {
        return new int[] { value & 0xFF, (value >> 8) & 0xFF };
    }

    public static final class ParsedDatagram {
        private final int cmd;
        private final int idx;
        private final int adp;
        private final int ado;
        private final int dataLength;
        private final boolean moreFollowing;
        private final int irq;
        private final int[] data;
        private final int workingCounter;

        ParsedDatagram(
                int cmd,
                int idx,
                int adp,
                int ado,
                int dataLength,
                boolean moreFollowing,
                int irq,
                int[] data,
                int workingCounter
        ) {
            this.cmd = cmd;
            this.idx = idx;
            this.adp = adp;
            this.ado = ado;
            this.dataLength = dataLength;
            this.moreFollowing = moreFollowing;
            this.irq = irq;
            this.data = data == null ? new int[0] : data.clone();
            this.workingCounter = workingCounter;
        }

        public int cmd() {
            return cmd;
        }

        public int idx() {
            return idx;
        }

        public int adp() {
            return adp;
        }

        public int ado() {
            return ado;
        }

        public int dataLength() {
            return dataLength;
        }

        public boolean moreFollowing() {
            return moreFollowing;
        }

        public int irq() {
            return irq;
        }

        public int[] data() {
            return data.clone();
        }

        public int workingCounter() {
            return workingCounter;
        }
    }
}
