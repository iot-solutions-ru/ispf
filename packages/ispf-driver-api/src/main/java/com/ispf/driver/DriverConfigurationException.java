package com.ispf.driver;

/**
 * Driver configuration or point address is invalid (missing host, bad register syntax,
 * unknown unit id, …). Operator action is required; retrying is pointless.
 */
public class DriverConfigurationException extends DriverException {

    public DriverConfigurationException(String message) {
        super(DriverErrorKind.CONFIGURATION, message, null);
    }

    public DriverConfigurationException(String message, Throwable cause) {
        super(DriverErrorKind.CONFIGURATION, message, cause);
    }
}
