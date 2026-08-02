package com.ispf.driver.iec104.codec;

public final class Iec104Cause {

    public static final int SPONTANEOUS = 3;
    public static final int ACTIVATION = 6;
    public static final int ACTIVATION_CONFIRMATION = 7;
    public static final int INTERROGATED_BY_STATION = 20;
    public static final int REQUEST = 5;

    private Iec104Cause() {
    }
}
