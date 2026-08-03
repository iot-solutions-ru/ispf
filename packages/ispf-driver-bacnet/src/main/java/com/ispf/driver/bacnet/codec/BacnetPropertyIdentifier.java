package com.ispf.driver.bacnet.codec;

/**
 * BACnet property identifiers used by the driver.
 */
public enum BacnetPropertyIdentifier {
    PRESENT_VALUE(85, "present-value"),
    UNITS(117, "units");

    private final int id;
    private final String protocolName;

    BacnetPropertyIdentifier(int id, String protocolName) {
        this.id = id;
        this.protocolName = protocolName;
    }

    public int id() {
        return id;
    }

    public String protocolName() {
        return protocolName;
    }

    public static BacnetPropertyIdentifier fromId(int id) {
        for (BacnetPropertyIdentifier property : values()) {
            if (property.id == id) {
                return property;
            }
        }
        throw new IllegalArgumentException("Unsupported BACnet property id: " + id);
    }

    public static BacnetPropertyIdentifier fromName(String name) {
        String normalized = name.trim().toLowerCase().replace('_', '-');
        for (BacnetPropertyIdentifier property : values()) {
            if (property.protocolName.equals(normalized)) {
                return property;
            }
        }
        throw new IllegalArgumentException("Unsupported BACnet property: " + name);
    }

    @Override
    public String toString() {
        return protocolName;
    }
}
