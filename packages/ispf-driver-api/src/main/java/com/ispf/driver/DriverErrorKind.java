package com.ispf.driver;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;

/**
 * Coarse classification of a driver failure. Lets the runtime distinguish
 * "retry later" from "fix the configuration" and "this is a bug" without
 * parsing exception messages.
 *
 * <p>Drivers should throw one of the typed subclasses of {@link DriverException}
 * ({@link DriverTransientException}, {@link DriverPermanentException},
 * {@link DriverConfigurationException}, {@link DriverUnsupportedOperationException}).
 * For plain {@link DriverException} or foreign exceptions, {@link #classify(Throwable)}
 * applies a best-effort mapping.
 */
public enum DriverErrorKind {

    /** Network / device temporarily unavailable — timeouts, connection reset, backpressure. Retry is reasonable. */
    TRANSIENT,

    /** Device or protocol rejected the operation and will keep rejecting it — malformed response, protocol violation, permission denied. */
    PERMANENT,

    /** Driver configuration or point address is invalid — operator action required. */
    CONFIGURATION,

    /** Operation not implemented or not supported by this driver / device model. */
    UNSUPPORTED,

    /** Could not be classified — plain {@link DriverException} or an unexpected runtime exception. */
    UNCLASSIFIED;

    /** Lower-case tag value for metrics and logs. */
    public String tag() {
        return name().toLowerCase();
    }

    /**
     * Best-effort classification of any throwable raised by a driver call.
     * Typed {@link DriverException}s report their own kind; common JDK exceptions
     * are mapped heuristically; everything else is {@link #UNCLASSIFIED}.
     */
    public static DriverErrorKind classify(Throwable error) {
        if (error == null) {
            return UNCLASSIFIED;
        }
        if (error instanceof DriverException driverException) {
            DriverErrorKind declared = driverException.kind();
            if (declared != UNCLASSIFIED) {
                return declared;
            }
            Throwable cause = driverException.getCause();
            return cause != null && cause != error ? classifyForeign(cause) : UNCLASSIFIED;
        }
        return classifyForeign(error);
    }

    private static DriverErrorKind classifyForeign(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof ClosedChannelException
                    || current instanceof InterruptedException) {
                return TRANSIENT;
            }
            if (current instanceof UnknownHostException) {
                return CONFIGURATION;
            }
            if (current instanceof UnsupportedOperationException) {
                return UNSUPPORTED;
            }
            if (current instanceof IllegalArgumentException) {
                return CONFIGURATION;
            }
            if (current instanceof IOException) {
                return TRANSIENT;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
            depth++;
        }
        return UNCLASSIFIED;
    }
}
