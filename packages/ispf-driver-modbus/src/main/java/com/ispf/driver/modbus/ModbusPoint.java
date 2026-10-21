package com.ispf.driver.modbus;

import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverException;

import java.util.Locale;
import java.util.Set;

/**
 * Parsed Modbus point from mapping
 * {@code slaveId:registerType:address[:count]:dataType[:byteOrder]}.
 * <p>
 * Canonical examples:
 * <ul>
 *   <li>{@code 1:HOLDING:10}</li>
 *   <li>{@code 1:HOLDING:0:FLOAT32:BADC}</li>
 *   <li>{@code 1:HOLDING:0:INT32:ABCD}</li>
 *   <li>{@code 1:HOLDING:0:DOUBLE:DCBA}</li>
 * </ul>
 */
record ModbusPoint(
        int slaveId,
        RegisterType type,
        int address,
        int count,
        DataType dataType,
        ByteOrder byteOrder
) {

    private static final Set<String> BYTE_ORDERS = Set.of("ABCD", "BADC", "CDAB", "DCBA");

    enum RegisterType {
        HOLDING, INPUT, COIL, DISCRETE
    }

    enum DataType {
        /** Legacy: concatenate registers as unsigned, {@code value = (double) raw}. */
        RAW,
        INT16,
        UINT16,
        INT32,
        UINT32,
        FLOAT32,
        DOUBLE
    }

    /**
     * Byte layout across registers (high register first on the wire).
     * ABCD = big-endian, BADC = big-endian byte swap, CDAB = word swap, DCBA = little-endian.
     */
    enum ByteOrder {
        ABCD, BADC, CDAB, DCBA
    }

    static ModbusPoint parse(String mapping) throws DriverException {
        String[] parts = mapping.split(":");
        if (parts.length < 3) {
            throw new DriverConfigurationException(
                    "Invalid Modbus mapping (expected slaveId:type:address[:count]:dataType[:byteOrder]): " + mapping
            );
        }
        try {
            int slaveId = Integer.parseInt(parts[0].trim());
            RegisterType type = RegisterType.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            int address = Integer.parseInt(parts[2].trim());

            DataType dataType = DataType.RAW;
            ByteOrder byteOrder = ByteOrder.ABCD;
            Integer explicitCount = null;
            int index = 3;

            if (parts.length > index && isInteger(parts[index].trim())) {
                explicitCount = Integer.parseInt(parts[index].trim());
                index++;
            }

            if (parts.length > index) {
                String token = normalizeToken(parts[index].trim());
                index++;

                ParsedDisplay display = tryParseCombinedDisplay(token);
                if (display != null) {
                    dataType = display.dataType();
                    byteOrder = display.byteOrder();
                } else if (isByteOrderToken(token) && parts.length == index) {
                    byteOrder = parseByteOrder(token);
                    dataType = DataType.UINT32;
                } else {
                    dataType = parseDataType(token);
                    if (parts.length > index) {
                        byteOrder = parseByteOrder(normalizeToken(parts[index].trim()));
                        index++;
                    } else if (needsByteOrder(dataType)) {
                        byteOrder = ByteOrder.ABCD;
                    }
                }
            }

            if (parts.length > index) {
                throw new DriverConfigurationException(
                        "Invalid Modbus mapping (too many segments): " + mapping
                                + ". Use e.g. FLOAT32:BADC or INT32:ABCD"
                );
            }

            int count = resolveCount(dataType, explicitCount, mapping);
            return new ModbusPoint(slaveId, type, address, count, dataType, byteOrder);
        } catch (DriverException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new DriverConfigurationException(
                    "Invalid Modbus mapping: " + mapping + ". " + e.getMessage(),
                    e
            );
        }
    }

    private static int resolveCount(DataType dataType, Integer explicitCount, String mapping) {
        int required = switch (dataType) {
            case RAW -> explicitCount != null ? Math.max(1, explicitCount) : 1;
            case INT16, UINT16 -> 1;
            case INT32, UINT32, FLOAT32 -> 2;
            case DOUBLE -> 4;
        };
        if (explicitCount != null && dataType != DataType.RAW && explicitCount != required) {
            throw new IllegalArgumentException(
                    dataType + " requires exactly " + required + " register(s): " + mapping
            );
        }
        return required;
    }

    private static boolean needsByteOrder(DataType dataType) {
        return dataType == DataType.INT32
                || dataType == DataType.UINT32
                || dataType == DataType.FLOAT32
                || dataType == DataType.DOUBLE;
    }

    private static boolean isInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start >= value.length()) {
            return false;
        }
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static String normalizeToken(String raw) {
        return raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replace("__", "_");
    }

    private static boolean isByteOrderToken(String token) {
        return BYTE_ORDERS.contains(token)
                || token.equals("BE")
                || token.equals("LE")
                || token.equals("BE_BS")
                || token.equals("BYTE_SWAP")
                || token.equals("WORD_SWAP")
                || token.equals("BIG_ENDIAN")
                || token.equals("LITTLE_ENDIAN")
                || token.equals("BIG_ENDIAN_BYTE_SWAP")
                || token.equals("LITTLE_ENDIAN_WORD_SWAP");
    }

    private record ParsedDisplay(DataType dataType, ByteOrder byteOrder) {
    }

    /** Hidden alias e.g. {@code FLOAT_BADC}; docs use canonical {@code FLOAT32:BADC}. */
    private static ParsedDisplay tryParseCombinedDisplay(String token) {
        for (String order : BYTE_ORDERS) {
            if (token.endsWith("_" + order)) {
                String typePart = token.substring(0, token.length() - order.length() - 1);
                DataType dataType = parseDataTypeOrNull(typePart);
                if (dataType != null && needsByteOrder(dataType)) {
                    return new ParsedDisplay(dataType, ByteOrder.valueOf(order));
                }
            }
            if (token.endsWith(order) && token.length() > order.length()) {
                String typePart = token.substring(0, token.length() - order.length());
                if (typePart.endsWith("_")) {
                    typePart = typePart.substring(0, typePart.length() - 1);
                }
                DataType dataType = parseDataTypeOrNull(typePart);
                if (dataType != null && needsByteOrder(dataType)) {
                    return new ParsedDisplay(dataType, ByteOrder.valueOf(order));
                }
            }
        }
        return null;
    }

    private static DataType parseDataType(String token) {
        DataType parsed = parseDataTypeOrNull(token);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "Unknown data type '" + token + "'. Supported: "
                            + "INT16, UINT16, INT32, UINT32, FLOAT32, DOUBLE "
                            + "(optional order ABCD|BADC|CDAB|DCBA, e.g. FLOAT32:BADC)"
            );
        }
        return parsed;
    }

    private static DataType parseDataTypeOrNull(String token) {
        String t = normalizeToken(token);
        return switch (t) {
            case "RAW", "INT", "INTEGER", "HEX", "BINARY" -> DataType.RAW;
            case "SIGNED", "INT16", "SHORT", "16_BIT_SIGNED", "16BIT_SIGNED" -> DataType.INT16;
            case "UNSIGNED", "UINT16", "USHORT", "16_BIT_UNSIGNED", "16BIT_UNSIGNED" -> DataType.UINT16;
            case "LONG", "INT32", "SIGNED_LONG", "32_BIT_SIGNED", "32BIT_SIGNED",
                 "32_BIT_SIGNED_LONG", "32BIT_SIGNED_LONG" -> DataType.INT32;
            case "ULONG", "UINT32", "UNSIGNED_LONG", "32_BIT_UNSIGNED", "32BIT_UNSIGNED",
                 "32_BIT_UNSIGNED_LONG", "32BIT_UNSIGNED_LONG" -> DataType.UINT32;
            case "FLOAT", "FLOAT32", "REAL", "IEEE754", "32_BIT_FLOAT", "32BIT_FLOAT" -> DataType.FLOAT32;
            case "DOUBLE", "FLOAT64", "64_BIT_FLOAT", "64BIT_FLOAT", "64_BIT_DOUBLE", "64BIT_DOUBLE" ->
                    DataType.DOUBLE;
            default -> null;
        };
    }

    private static ByteOrder parseByteOrder(String raw) {
        String token = normalizeToken(raw);
        return switch (token) {
            case "ABCD", "BE", "BIG", "BIG_ENDIAN" -> ByteOrder.ABCD;
            case "BADC", "BE_BS", "BIG_ENDIAN_BYTE_SWAP", "BYTE_SWAP" -> ByteOrder.BADC;
            case "CDAB", "LE_WS", "LITTLE_ENDIAN_WORD_SWAP", "WORD_SWAP" -> ByteOrder.CDAB;
            case "DCBA", "LE", "LITTLE", "LITTLE_ENDIAN" -> ByteOrder.DCBA;
            default -> throw new IllegalArgumentException(
                    "Unknown byte order '" + raw + "'. Supported: ABCD, BADC, CDAB, DCBA"
            );
        };
    }
}
