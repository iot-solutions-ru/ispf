package com.ispf.driver;

/**
 * The requested operation (typically write or browse) is not implemented by this driver
 * or not supported by the device model. Distinct from a failure: nothing is broken.
 */
public class DriverUnsupportedOperationException extends DriverException {

    public DriverUnsupportedOperationException(String message) {
        super(DriverErrorKind.UNSUPPORTED, message, null);
    }

    public DriverUnsupportedOperationException(String message, Throwable cause) {
        super(DriverErrorKind.UNSUPPORTED, message, cause);
    }
}
