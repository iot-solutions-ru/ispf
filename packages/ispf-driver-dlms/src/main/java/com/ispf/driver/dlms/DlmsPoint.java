package com.ispf.driver.dlms;

import com.ispf.driver.DriverException;
import gurux.dlms.enums.ObjectType;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed DLMS/COSEM point from mapping string.
 * <p>
 * Forms:
 * <ul>
 *   <li>{@code logicalDevice:obis} — default {@link ObjectType#REGISTER}, attribute 2</li>
 *   <li>{@code logicalDevice:obis:objectType} — attribute 2</li>
 *   <li>{@code logicalDevice:obis:objectType:attribute}</li>
 * </ul>
 * Example: {@code 1:1.0.1.8.0.255} or {@code 1:0.0.42.0.0.255:DATA:2}.
 */
record DlmsPoint(int logicalDevice, String obis, ObjectType objectType, int attributeIndex) {

    private static final Pattern MAPPING = Pattern.compile(
            "^(\\d+):((?:\\d+\\.){5}\\d+)(?::([A-Za-z_]+))?(?::(\\d+))?$"
    );

    static DlmsPoint parse(String mapping) throws DriverException {
        if (mapping == null || mapping.isBlank()) {
            throw new DriverException("DLMS mapping requires logicalDevice:obis: " + mapping);
        }
        Matcher matcher = MAPPING.matcher(mapping.trim());
        if (!matcher.matches()) {
            throw new DriverException("Invalid DLMS mapping (expected logicalDevice:obis[:type[:attr]]): " + mapping);
        }
        try {
            int logicalDevice = Integer.parseInt(matcher.group(1));
            String obis = matcher.group(2);
            String typeName = matcher.group(3);
            ObjectType objectType = ObjectType.REGISTER;
            if (typeName != null && !typeName.isBlank()) {
                objectType = ObjectType.valueOf(typeName.trim().toUpperCase(Locale.ROOT));
            }
            int attributeIndex = 2;
            if (matcher.group(4) != null) {
                attributeIndex = Integer.parseInt(matcher.group(4));
            }
            return new DlmsPoint(logicalDevice, obis, objectType, attributeIndex);
        } catch (IllegalArgumentException ex) {
            throw new DriverException("Invalid DLMS mapping: " + mapping, ex);
        }
    }
}
