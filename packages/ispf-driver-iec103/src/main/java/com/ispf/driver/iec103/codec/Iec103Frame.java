package com.ispf.driver.iec103.codec;

/**
 * One FT1.2 frame: single-character {@code E5}, fixed {@code 10h}, or variable {@code 68h}.
 * ASDU octets are held in a defensive copy (not exposed as a record component).
 */
public final class Iec103Frame {

    public enum Kind {
        SINGLE_ACK,
        FIXED,
        VARIABLE
    }

    private final Kind kind;
    private final int control;
    private final int linkAddress;
    private final byte[] asdu;

    private Iec103Frame(Kind kind, int control, int linkAddress, byte[] asdu) {
        this.kind = kind;
        this.control = control;
        this.linkAddress = linkAddress;
        this.asdu = asdu == null ? new byte[0] : asdu.clone();
    }

    public static Iec103Frame singleAck() {
        return new Iec103Frame(Kind.SINGLE_ACK, 0, 0, new byte[0]);
    }

    public static Iec103Frame fixed(int control, int linkAddress) {
        return new Iec103Frame(Kind.FIXED, control, linkAddress, new byte[0]);
    }

    public static Iec103Frame variable(int control, int linkAddress, byte[] asdu) {
        return new Iec103Frame(Kind.VARIABLE, control, linkAddress, asdu);
    }

    public Kind kind() {
        return kind;
    }

    public int control() {
        return control;
    }

    public int linkAddress() {
        return linkAddress;
    }

    public byte[] asdu() {
        return asdu.clone();
    }
}
