package com.ispf.driver.smb;

/**
 * Point mapping: file path within the configured SMB share.
 */
public record SmbPoint(String filePath) {

    public static SmbPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("SMB point mapping is blank");
        }
        String path = raw.trim();
        while (path.startsWith("/") || path.startsWith("\\")) {
            path = path.substring(1);
        }
        return new SmbPoint(path);
    }
}
