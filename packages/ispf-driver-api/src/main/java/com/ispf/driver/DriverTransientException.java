package com.ispf.driver;

/**
 * Temporary failure — network timeout, connection reset, device busy.
 * The runtime keeps the binding active and retries on the next poll.
 */
public class DriverTransientException extends DriverException {

    public DriverTransientException(String message) {
        super(DriverErrorKind.TRANSIENT, message, null);
    }

    public DriverTransientException(String message, Throwable cause) {
        super(DriverErrorKind.TRANSIENT, message, cause);
    }
}
