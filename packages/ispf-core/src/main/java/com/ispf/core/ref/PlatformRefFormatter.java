package com.ispf.core.ref;

/**
 * Formats {@link PlatformRef} to canonical slash grammar.
 */
public final class PlatformRefFormatter {

    private PlatformRefFormatter() {
    }

    public static String format(PlatformRef ref) {
        if (ref == null) {
            return "";
        }
        String object = ref.object();
        String kindSegment = ref.kind().segment();
        if (kindSegment != null) {
            return object + "/" + kindSegment + "/" + ref.name();
        }
        if (PlatformRef.DEFAULT_FIELD.equals(ref.field())) {
            return object + "/" + ref.name();
        }
        return object + "/" + ref.name() + "/" + ref.field();
    }
}
