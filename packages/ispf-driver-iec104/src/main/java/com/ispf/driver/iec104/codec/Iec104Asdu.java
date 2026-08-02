package com.ispf.driver.iec104.codec;

import java.util.List;

public record Iec104Asdu(int typeId, int cause, int originatorAddress, int commonAddress, List<Iec104Value> values) {

    public Iec104Asdu {
        values = List.copyOf(values);
    }

    public static Iec104Asdu single(int typeId, int cause, int commonAddress, Iec104Value value) {
        return new Iec104Asdu(typeId, cause, 0, commonAddress, List.of(value));
    }
}
