package com.ispf.driver.iec101.codec;

/**
 * One FT1.2 frame: single-character {@code E5}, fixed {@code 10h}, or variable {@code 68h}.
 */
public record Iec101Frame(Kind kind, int control, int linkAddress, byte[] asdu) {

    public enum Kind {
        SINGLE_ACK,
        FIXED,
        VARIABLE
    }

    public Iec101Frame {
        asdu = asdu == null ? new byte[0] : asdu.clone();
    }

    public static Iec101Frame singleAck() {
        return new Iec101Frame(Kind.SINGLE_ACK, 0, 0, new byte[0]);
    }

    public static Iec101Frame fixed(int control, int linkAddress) {
        return new Iec101Frame(Kind.FIXED, control, linkAddress, new byte[0]);
    }

    public static Iec101Frame variable(int control, int linkAddress, byte[] asdu) {
        return new Iec101Frame(Kind.VARIABLE, control, linkAddress, asdu);
    }

    @Override
    public byte[] asdu() {
        return asdu.clone();
    }
}
