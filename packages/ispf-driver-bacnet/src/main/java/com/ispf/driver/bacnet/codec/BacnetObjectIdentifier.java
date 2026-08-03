package com.ispf.driver.bacnet.codec;

/**
 * BACnet object identifier encoded as object type plus instance number.
 */
public record BacnetObjectIdentifier(BacnetObjectType type, int instance) {

    public BacnetObjectIdentifier {
        if (instance < 0 || instance > 0x3F_FFFF) {
            throw new IllegalArgumentException("BACnet object instance out of range: " + instance);
        }
    }

    int encoded() {
        return (type.id() << 22) | instance;
    }

    static BacnetObjectIdentifier decode(int encoded) {
        int typeId = (encoded >>> 22) & 0x3FF;
        int instance = encoded & 0x3F_FFFF;
        return new BacnetObjectIdentifier(BacnetObjectType.fromId(typeId), instance);
    }

    @Override
    public String toString() {
        return type + ":" + instance;
    }
}
