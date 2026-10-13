package com.ispf.server.ai.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline feature-hash embedder — must stay aligned with {@code tools/ai-pack/build.py} {@code hash_embed}.
 */
public final class ContextPackHashEmbedder {

    public static final String MODEL_ID = "ispf-hash-v1";
    public static final int DIMS = 384;

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_]+");

    private ContextPackHashEmbedder() {
    }

    public static boolean isHashModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String key = modelId.trim().toLowerCase(Locale.ROOT);
        return MODEL_ID.equals(key) || "ispf-hash".equals(key) || "hash".equals(key);
    }

    public static float[] embed(String text) {
        return embed(text, DIMS);
    }

    public static float[] embed(String text, int dims) {
        int size = dims > 0 ? dims : DIMS;
        float[] vec = new float[size];
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
        for (String token : tokenize(text)) {
            digest.reset();
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            int idx = ((hash[0] & 0xff) << 24
                    | (hash[1] & 0xff) << 16
                    | (hash[2] & 0xff) << 8
                    | (hash[3] & 0xff)) & 0x7fff_ffff;
            idx = idx % size;
            float sign = ((hash[4] & 0xff) % 2 == 0) ? 1.0f : -1.0f;
            vec[idx] += sign;
        }
        return l2Normalize(vec);
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    public static float[] toFloatArray(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Number number) {
                out[i] = number.floatValue();
            } else {
                try {
                    out[i] = Float.parseFloat(String.valueOf(item));
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return out;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static float[] l2Normalize(float[] vec) {
        double sum = 0.0;
        for (float v : vec) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm <= 0.0) {
            return vec;
        }
        float[] out = new float[vec.length];
        for (int i = 0; i < vec.length; i++) {
            out[i] = (float) (vec[i] / norm);
        }
        return out;
    }
}
