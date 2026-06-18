package com.ispf.driver;

/**
 * Driver operation failure.
 */
public class DriverException extends Exception {

    public DriverException(String message) {
        super(message);
    }

    public DriverException(String message, Throwable cause) {
        super(message, cause);
    }
}
