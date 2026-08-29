package com.ispf.driver.websocket;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * WebSocket protocol stub (websocket).
 * <p>
 * Generic WebSocket telemetry stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class WebsocketDeviceDriver extends ProtocolStubDeviceDriver {

    public WebsocketDeviceDriver() {
        super(
                "websocket",
                "WebSocket Driver",
                "Generic WebSocket telemetry stub",
                8080
        );
    }
}
