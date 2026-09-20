package com.ispf.driver;

/**
 * The device or protocol rejected the operation in a way that will not change on retry —
 * malformed response, protocol violation, authentication / permission denied.
 */
public class DriverPermanentException extends DriverException {

    public DriverPermanentException(String message) {
        super(DriverErrorKind.PERMANENT, message, null);
    }

    public DriverPermanentException(String message, Throwable cause) {
        super(DriverErrorKind.PERMANENT, message, cause);
    }
}
