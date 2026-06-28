package com.ispf.driver.dnp3;

import io.stepfunc.dnp3.AnalogInput;
import io.stepfunc.dnp3.AnalogOutputStatus;
import io.stepfunc.dnp3.BinaryInput;
import io.stepfunc.dnp3.BinaryOutputStatus;
import io.stepfunc.dnp3.Counter;
import io.stepfunc.dnp3.HeaderInfo;
import io.stepfunc.dnp3.ReadHandler;
import io.stepfunc.dnp3.ReadType;
import io.stepfunc.dnp3.ResponseHeader;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects measurement values from DNP3 read responses (Class 0/1/2/3 integrity poll).
 */
final class Dnp3ReadCache implements ReadHandler {

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
    public void handleBinaryInput(HeaderInfo info, List<BinaryInput> values) {
        for (BinaryInput point : values) {
            int index = point.index.intValue();
            binaryInputs.put(index, point.value);
            qualities.put(indexKey(Dnp3Point.Dnp3DataType.BINARY_INPUT, index), formatFlags(point.flags.value.intValue()));
        }
    }

    @Override
    public void handleBinaryOutputStatus(HeaderInfo info, List<BinaryOutputStatus> values) {
        for (BinaryOutputStatus point : values) {
            int index = point.index.intValue();
            binaryOutputs.put(index, point.value);
            qualities.put(indexKey(Dnp3Point.Dnp3DataType.BINARY_OUTPUT, index), formatFlags(point.flags.value.intValue()));
        }
    }

    @Override
    public void handleAnalogInput(HeaderInfo info, List<AnalogInput> values) {
        for (AnalogInput point : values) {
            int index = point.index.intValue();
            analogInputs.put(index, point.value);
            qualities.put(indexKey(Dnp3Point.Dnp3DataType.ANALOG_INPUT, index), formatFlags(point.flags.value.intValue()));
        }
    }

    @Override
    public void handleAnalogOutputStatus(HeaderInfo info, List<AnalogOutputStatus> values) {
        for (AnalogOutputStatus point : values) {
            int index = point.index.intValue();
            analogOutputs.put(index, point.value);
            qualities.put(indexKey(Dnp3Point.Dnp3DataType.ANALOG_OUTPUT, index), formatFlags(point.flags.value.intValue()));
        }
    }

    @Override
    public void handleCounter(HeaderInfo info, List<Counter> values) {
        for (Counter point : values) {
            int index = point.index.intValue();
            counters.put(index, point.value.longValue());
            qualities.put(indexKey(Dnp3Point.Dnp3DataType.COUNTER, index), formatFlags(point.flags.value.intValue()));
        }
    }

    @Override
    public void beginFragment(ReadType readType, ResponseHeader header) {
        // no-op
    }

    @Override
    public void endFragment(ReadType readType, ResponseHeader header) {
        // no-op
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
