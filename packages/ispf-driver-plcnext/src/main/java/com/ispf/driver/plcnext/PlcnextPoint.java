package com.ispf.driver.plcnext;

/**
 * PLCnext RSC-lab symbol path point.
 * <p>
 * Accepted forms are IEC/RSC-style symbol paths such as
 * {@code Arp.Plc.Eclr/MainInstance.xMotor}. Paths must contain at least one {@code .} or {@code /}.
 */
public record PlcnextPoint(String path) {

    public static PlcnextPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("PLCnext point mapping is blank");
        }
        String path = raw.trim();
        if (!path.contains(".") && !path.contains("/")) {
            throw new IllegalArgumentException(
                    "PLCnext point must be a symbol path, e.g. Arp.Plc.Eclr/MainInstance.xMotor"
            );
        }
        return new PlcnextPoint(path);
    }

    @Override
    public String toString() {
        return path;
    }
}
