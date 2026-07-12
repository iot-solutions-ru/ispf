package com.ispf.core.ref;

/**
 * Invalid PlatformRef text or legacy alias.
 */
public final class PlatformRefParseException extends RuntimeException {

    public PlatformRefParseException(String message) {
        super(message);
    }

    public PlatformRefParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
