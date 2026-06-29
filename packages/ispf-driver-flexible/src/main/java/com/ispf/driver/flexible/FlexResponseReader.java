package com.ispf.driver.flexible;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads TCP/UDP response bytes using idle or delimiter mode.
 */
final class FlexResponseReader {

    private FlexResponseReader() {
    }

    static byte[] read(InputStream in, String readMode, int delimiterByte, int maxBytes, int timeoutMs)
            throws IOException {
        if ("delimiter".equalsIgnoreCase(readMode)) {
            return readUntilDelimiter(in, delimiterByte, maxBytes, timeoutMs);
        }
        return readIdle(in, maxBytes, timeoutMs);
    }

    private static byte[] readIdle(InputStream in, int maxBytes, int timeoutMs) throws IOException {
        byte[] buffer = new byte[maxBytes];
        int total = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && total < maxBytes) {
            while (in.available() > 0 && total < maxBytes) {
                int read = in.read(buffer, total, maxBytes - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            if (total > 0 && in.available() == 0) {
                break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        byte[] out = new byte[total];
        System.arraycopy(buffer, 0, out, 0, total);
        return out;
    }

    private static byte[] readUntilDelimiter(InputStream in, int delimiterByte, int maxBytes, int timeoutMs)
            throws IOException {
        byte[] buffer = new byte[maxBytes];
        int total = 0;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && total < maxBytes) {
            int read = in.read();
            if (read < 0) {
                break;
            }
            buffer[total++] = (byte) read;
            if (read == delimiterByte) {
                break;
            }
        }
        byte[] out = new byte[total];
        System.arraycopy(buffer, 0, out, 0, total);
        return out;
    }
}
