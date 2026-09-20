package com.ispf.driver;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverErrorKindTest {

    @Test
    void typedSubclassesReportTheirKind() {
        assertEquals(DriverErrorKind.TRANSIENT, new DriverTransientException("timeout").kind());
        assertEquals(DriverErrorKind.PERMANENT, new DriverPermanentException("bad frame").kind());
        assertEquals(DriverErrorKind.CONFIGURATION, new DriverConfigurationException("no host").kind());
        assertEquals(DriverErrorKind.UNSUPPORTED, new DriverUnsupportedOperationException("write").kind());
        assertEquals(DriverErrorKind.UNCLASSIFIED, new DriverException("plain").kind());
    }

    @Test
    void classifyPrefersDeclaredKindOverCause() {
        DriverException typed = new DriverPermanentException("rejected", new SocketTimeoutException());
        assertEquals(DriverErrorKind.PERMANENT, DriverErrorKind.classify(typed));
    }

    @Test
    void classifyFallsBackToCauseForPlainDriverException() {
        assertEquals(
                DriverErrorKind.TRANSIENT,
                DriverErrorKind.classify(new DriverException("read failed", new SocketTimeoutException("t/o")))
        );
        assertEquals(
                DriverErrorKind.CONFIGURATION,
                DriverErrorKind.classify(new DriverException("resolve", new UnknownHostException("plc.local")))
        );
        assertEquals(DriverErrorKind.UNCLASSIFIED, DriverErrorKind.classify(new DriverException("no cause")));
    }

    @Test
    void classifyMapsCommonJdkExceptions() {
        assertEquals(DriverErrorKind.TRANSIENT, DriverErrorKind.classify(new ConnectException("refused")));
        assertEquals(DriverErrorKind.TRANSIENT, DriverErrorKind.classify(new IOException("reset")));
        assertEquals(DriverErrorKind.CONFIGURATION, DriverErrorKind.classify(new IllegalArgumentException("bad reg")));
        assertEquals(DriverErrorKind.CONFIGURATION, DriverErrorKind.classify(new NumberFormatException("x")));
        assertEquals(DriverErrorKind.UNSUPPORTED, DriverErrorKind.classify(new UnsupportedOperationException()));
        assertEquals(DriverErrorKind.UNCLASSIFIED, DriverErrorKind.classify(new IllegalStateException("bug")));
        assertEquals(DriverErrorKind.UNCLASSIFIED, DriverErrorKind.classify(null));
    }

    @Test
    void classifyWalksWrappedCauses() {
        RuntimeException wrapped = new RuntimeException(new IllegalStateException(new SocketTimeoutException()));
        assertEquals(DriverErrorKind.TRANSIENT, DriverErrorKind.classify(wrapped));
    }

    @Test
    void tagIsLowerCase() {
        assertEquals("transient", DriverErrorKind.TRANSIENT.tag());
        assertEquals("unclassified", DriverErrorKind.UNCLASSIFIED.tag());
    }
}
