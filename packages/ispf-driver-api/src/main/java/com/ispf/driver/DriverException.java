package com.ispf.driver;

/**
 * Driver operation failure.
 *
 * <p>Prefer the typed subclasses so the runtime can react per {@link DriverErrorKind}:
 * <ul>
 *   <li>{@link DriverTransientException} — timeouts, connection lost; retry on the next poll.</li>
 *   <li>{@link DriverPermanentException} — protocol violation, device rejected; retry will not help.</li>
 *   <li>{@link DriverConfigurationException} — bad config / point address; operator action required.</li>
 *   <li>{@link DriverUnsupportedOperationException} — write or browse not supported by the driver.</li>
 * </ul>
 * Plain {@code DriverException} stays {@link DriverErrorKind#UNCLASSIFIED} and is classified
 * heuristically from its cause by {@link DriverErrorKind#classify(Throwable)}.
 */
public class DriverException extends Exception {

    private final DriverErrorKind kind;

    public DriverException(String message) {
        this(DriverErrorKind.UNCLASSIFIED, message, null);
    }

    public DriverException(String message, Throwable cause) {
        this(DriverErrorKind.UNCLASSIFIED, message, cause);
    }

    protected DriverException(DriverErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind == null ? DriverErrorKind.UNCLASSIFIED : kind;
    }

    /** Declared classification; {@link DriverErrorKind#UNCLASSIFIED} for the base class. */
    public DriverErrorKind kind() {
        return kind;
    }
}
