package com.ispf.server.security.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30s step) with RFC 4648 base32 secrets.
 */
public final class TotpUtil {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    static String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES];
        RANDOM.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    static String buildOtpauthUri(String issuer, String label, String secret) {
        return "otpauth://totp/"
                + urlEncode(issuer + ":" + label)
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + CODE_DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    static boolean verifyCode(String secret, String code, int windowSteps, Instant instant) {
        if (secret == null || secret.isBlank() || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long counter = instant.getEpochSecond() / TIME_STEP_SECONDS;
        for (int offset = -windowSteps; offset <= windowSteps; offset++) {
            if (code.equals(generateCode(secret, counter + offset))) {
                return true;
            }
        }
        return false;
    }

    static String generateCode(String secret, Instant instant) {
        return generateCode(secret, instant.getEpochSecond() / TIME_STEP_SECONDS);
    }

    static String generateCode(String secret, long counter) {
        byte[] key = decodeBase32(secret);
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP HMAC failed", ex);
        }
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder encoded = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                encoded.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            encoded.append(BASE32_ALPHABET.charAt(index));
        }
        return encoded.toString();
    }

    private static byte[] decodeBase32(String secret) {
        String normalized = secret.trim().replace("=", "").toUpperCase();
        ByteBuffer buffer = ByteBuffer.allocate(normalized.length() * 5 / 8 + 1);
        int bits = 0;
        int value = 0;
        for (char ch : normalized.toCharArray()) {
            int index = BASE32_ALPHABET.indexOf(ch);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            value = (value << 5) | index;
            bits += 5;
            if (bits >= 8) {
                buffer.put((byte) ((value >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        buffer.flip();
        byte[] decoded = new byte[buffer.remaining()];
        buffer.get(decoded);
        return decoded;
    }

    private static String urlEncode(String value) {
        return value.replace(" ", "%20");
    }
}
