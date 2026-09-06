package com.ispf.driver.opcuapubsub;

import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OPC UA PubSub UADP/UDP lab point.
 * <p>
 * Forms: {@code ds:1}, {@code field:0}, {@code ns:2;s=Temp}.
 */
record OpcuaPubsubPoint(Kind kind, int datasetId, int fieldIndex, int namespaceIndex, String identifier) {

    enum Kind {
        DATASET,
        FIELD,
        NODE
    }

    private static final Pattern DATASET = Pattern.compile(
            "^ds\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD = Pattern.compile(
            "^field\\s*[:=]\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE = Pattern.compile(
            "^ns\\s*[:=]\\s*(\\d+)\\s*;\\s*s\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    static OpcuaPubsubPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("OPC UA PubSub lab point mapping is blank");
        }
        String trimmed = mapping.trim();
        Matcher dataset = DATASET.matcher(trimmed);
        if (dataset.matches()) {
            int ds = Integer.parseInt(dataset.group(1));
            if (ds < 0 || ds > 0xFFFF) {
                throw new DriverException("OPC UA PubSub lab dataset id out of range: " + ds);
            }
            return new OpcuaPubsubPoint(Kind.DATASET, ds, 0, 0, "");
        }
        Matcher field = FIELD.matcher(trimmed);
        if (field.matches()) {
            int idx = Integer.parseInt(field.group(1));
            if (idx < 0 || idx > 0xFFFF) {
                throw new DriverException("OPC UA PubSub lab field index out of range: " + idx);
            }
            return new OpcuaPubsubPoint(Kind.FIELD, 1, idx, 0, "");
        }
        Matcher node = NODE.matcher(trimmed);
        if (node.matches()) {
            int ns = Integer.parseInt(node.group(1));
            String id = node.group(2).trim();
            if (ns < 0 || ns > 0xFFFF || id.isEmpty()) {
                throw new DriverException("OPC UA PubSub lab node mapping incomplete: " + mapping);
            }
            return new OpcuaPubsubPoint(Kind.NODE, 0, 0, ns, id);
        }
        throw new DriverException(
                "Unsupported OPC UA PubSub lab mapping (expected ds:1, field:0, or ns:2;s=Temp): "
                        + mapping);
    }

    /** Canonical wire key exchanged in the UADP-lab datagram. */
    String wireToken() {
        return switch (kind) {
            case DATASET -> "ds:" + datasetId;
            case FIELD -> "field:" + fieldIndex;
            case NODE -> "ns:" + namespaceIndex + ";s=" + identifier;
        };
    }

    String display() {
        return wireToken().toLowerCase(Locale.ROOT);
    }
}
