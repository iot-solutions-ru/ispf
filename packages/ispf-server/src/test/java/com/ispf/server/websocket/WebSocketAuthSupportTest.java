package com.ispf.server.websocket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebSocketAuthSupportTest {

    @Test
    void parsesBearerAuthorizationHeader() {
        assertEquals("abc123", WebSocketAuthSupport.bearerFromAuthorization("Bearer abc123"));
    }

    @Test
    void parsesSubprotocolTokenPair() {
        assertEquals(
                "tok-xyz",
                WebSocketAuthSupport.bearerFromSubprotocol(List.of("ispf-bearer, tok-xyz"))
        );
    }

    @Test
    void parsesDottedSubprotocolToken() {
        assertEquals(
                "jwt.part.sig",
                WebSocketAuthSupport.bearerFromSubprotocol(List.of("ispf-bearer.jwt.part.sig"))
        );
    }

    @Test
    void returnsNullWhenMissing() {
        assertNull(WebSocketAuthSupport.bearerFromSubprotocol(List.of()));
        assertNull(WebSocketAuthSupport.bearerFromAuthorization(null));
    }
}
