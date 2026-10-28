package com.ispf.driver;

/**
 * The driver will try {@code connect()} again on a later poll. The runtime keeps the binding
 * and does not treat this wait as a new failure worth a warning.
 */
public class DriverReconnectPendingException extends DriverTransientException {

    public DriverReconnectPendingException(String message) {
        super(message);
    }
}
