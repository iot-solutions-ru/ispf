package com.ispf.driver.profibus.codec;

/**
 * One PROFIBUS FDL frame: SD1 fixed, SD2 variable, or SD3 fixed-data.
 * PDU octets are held privately (not as a record component).
 */
public final class ProfibusFdlFrame {

    public enum Kind {
        SD1,
        SD2,
        SD3
    }

    private final Kind kind;
    private final int da;
    private final int sa;
    private final int fc;
    private final byte[] pdu;

    private ProfibusFdlFrame(Kind kind, int da, int sa, int fc, byte[] pdu) {
        this.kind = kind;
        this.da = da & 0xFF;
        this.sa = sa & 0xFF;
        this.fc = fc & 0xFF;
        this.pdu = pdu == null ? new byte[0] : pdu.clone();
    }

    public static ProfibusFdlFrame sd1(int da, int sa, int fc) {
        return new ProfibusFdlFrame(Kind.SD1, da, sa, fc, new byte[0]);
    }

    public static ProfibusFdlFrame sd2(int da, int sa, int fc, byte[] pdu) {
        return new ProfibusFdlFrame(Kind.SD2, da, sa, fc, pdu);
    }

    public static ProfibusFdlFrame sd3(int da, int sa, int fc, byte[] data8) {
        byte[] data = data8 == null ? new byte[8] : data8.clone();
        if (data.length != 8) {
            throw new IllegalArgumentException("PROFIBUS FDL SD3 requires exactly 8 data octets");
        }
        return new ProfibusFdlFrame(Kind.SD3, da, sa, fc, data);
    }

    public Kind kind() {
        return kind;
    }

    public int da() {
        return da;
    }

    public int sa() {
        return sa;
    }

    public int fc() {
        return fc;
    }

    public byte[] pdu() {
        return pdu.clone();
    }
}
