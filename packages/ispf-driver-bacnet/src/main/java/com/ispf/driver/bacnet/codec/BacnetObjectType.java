package com.ispf.driver.bacnet.codec;

/**
 * BACnet object types supported by the clean-room driver codec.
 */
public enum BacnetObjectType {
    ANALOG_INPUT(0, "analog-input"),
    ANALOG_VALUE(2, "analog-value"),
    BINARY_INPUT(3, "binary-input"),
    BINARY_VALUE(5, "binary-value"),
    DEVICE(8, "device");

    private final int id;
    private final String protocolName;

    BacnetObjectType(int id, String protocolName) {
        this.id = id;
        this.protocolName = protocolName;
    }

    public int id() {
        return id;
    }

    public String protocolName() {
        return protocolName;
    }

    public boolean isAnalog() {
        return this == ANALOG_INPUT || this == ANALOG_VALUE;
    }

    public boolean isBinary() {
        return this == BINARY_INPUT || this == BINARY_VALUE;
    }

    public boolean isWritable() {
        return this == ANALOG_VALUE || this == BINARY_VALUE;
    }

    public static BacnetObjectType fromId(int id) {
        for (BacnetObjectType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported BACnet object type id: " + id);
    }

    public static BacnetObjectType fromName(String name) {
        String normalized = name.trim().toLowerCase().replace('_', '-');
        for (BacnetObjectType type : values()) {
            if (type.protocolName.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported BACnet object type: " + name);
    }

    @Override
    public String toString() {
        return protocolName;
    }
}
