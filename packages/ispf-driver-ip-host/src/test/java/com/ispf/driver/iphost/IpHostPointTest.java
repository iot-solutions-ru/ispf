package com.ispf.driver.iphost;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpHostPointTest {

    @Test
    void parsesModeTargetPort() {
        IpHostPoint point = IpHostPoint.parse("TCP:example.com:443", "127.0.0.1");
        assertEquals(IpHostPoint.IpHostMode.TCP, point.mode());
        assertEquals("example.com", point.target());
        assertEquals(443, point.port());
    }

    @Test
    void usesDefaultHostWhenTargetBlank() {
        IpHostPoint point = IpHostPoint.parse("PING:", "10.0.0.1");
        assertEquals("10.0.0.1", point.target());
    }

    @Test
    void usesDefaultPortForSmtp() {
        IpHostPoint point = IpHostPoint.parse("SMTP:mail.example.com", "127.0.0.1");
        assertEquals(25, point.port());
    }
}
