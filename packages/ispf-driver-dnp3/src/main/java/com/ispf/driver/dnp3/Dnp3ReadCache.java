package com.ispf.driver.dnp3;

import com.ispf.driver.dnp3.codec.Dnp3TcpCodec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects measurement values from DNP3 read responses (Class 0/1/2/3 integrity poll).
 */
final class Dnp3ReadCache implements Dnp3TcpCodec.MeasurementSink {

    private final Map<Integer, Boolean> binaryInputs = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> binaryOutputs = new ConcurrentHashMap<>();
    private final Map<Integer, Double> analogInputs = new ConcurrentHashMap<>();
    private final Map<Integer, Double> analogOutputs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> counters = new ConcurrentHashMap<>();
    private final Map<String, String> qualities = new ConcurrentHashMap<>();

    void clear() {
        binaryInputs.clear();
        binaryOutputs.clear();
        analogInputs.clear();
        analogOutputs.clear();
        counters.clear();
        qualities.clear();
    }

    @Override
    public void binary(Dnp3Point.Dnp3DataType type, int index, boolean value, int flags) {
        if (type == Dnp3Point.Dnp3DataType.BINARY_INPUT) {
            binaryInputs.put(index, value);
        } else {
            binaryOutputs.put(index, value);
        }
        qualities.put(indexKey(type, index), formatFlags(flags));
    }

    @Override
    public void analog(Dnp3Point.Dnp3DataType type, int index, double value, int flags) {
        if (type == Dnp3Point.Dnp3DataType.ANALOG_INPUT) {
            analogInputs.put(index, value);
        } else {
            analogOutputs.put(index, value);
        }
        qualities.put(indexKey(type, index), formatFlags(flags));
    }

    @Override
    public void counter(int index, long value, int flags) {
        counters.put(index, value);
        qualities.put(indexKey(Dnp3Point.Dnp3DataType.COUNTER, index), formatFlags(flags));
    }

    Object valueFor(Dnp3Point point) {
        return switch (point.dataType()) {
            case BINARY_INPUT -> binaryInputs.get(point.index());
            case BINARY_OUTPUT -> binaryOutputs.get(point.index());
            case ANALOG_INPUT -> analogInputs.get(point.index());
            case ANALOG_OUTPUT -> analogOutputs.get(point.index());
            case COUNTER -> counters.get(point.index());
        };
    }

    String qualityFor(Dnp3Point point) {
        return qualities.getOrDefault(indexKey(point.dataType(), point.index()), "UNKNOWN");
    }

    private static String indexKey(Dnp3Point.Dnp3DataType type, int index) {
        return type.name() + ":" + index;
    }

    private static String formatFlags(int flags) {
        return String.format("0x%02X", flags & 0xFF);
    }
}
