package com.ispf.server.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * SHA-256 chain over audit rows. Changing one row breaks every later link.
 */
public final class AuditChain {

    private AuditChain() {
    }

    public static String hash(
            String previousHash,
            String id,
            String category,
            String action,
            String actor,
            String targetType,
            String targetId,
            String detailsJson,
            Instant occurredAt
    ) {
        Instant normalizedAt = normalizeOccurredAt(occurredAt);
        String payload = String.join("\n",
                previousHash == null ? "" : previousHash,
                nullToEmpty(id),
                nullToEmpty(category),
                nullToEmpty(action),
                nullToEmpty(actor),
                nullToEmpty(targetType),
                nullToEmpty(targetId),
                nullToEmpty(detailsJson),
                normalizedAt == null ? "" : normalizedAt.toString()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("audit chain hash failed", ex);
        }
    }

    public static boolean intact(List<Link> links) {
        String previous = null;
        for (Link link : links) {
            String expected = hash(
                    previous,
                    link.id(),
                    link.category(),
                    link.action(),
                    link.actor(),
                    link.targetType(),
                    link.targetId(),
                    link.detailsJson(),
                    link.occurredAt()
            );
            if (!expected.equals(link.chainHash()) || !java.util.Objects.equals(previous, link.previousHash())) {
                return false;
            }
            previous = link.chainHash();
        }
        return true;
    }

    static Instant normalizeOccurredAt(Instant occurredAt) {
        if (occurredAt == null) {
            return null;
        }
        return occurredAt.truncatedTo(ChronoUnit.MICROS);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record Link(
            String id,
            String category,
            String action,
            String actor,
            String targetType,
            String targetId,
            String detailsJson,
            Instant occurredAt,
            String previousHash,
            String chainHash
    ) {
    }
}
