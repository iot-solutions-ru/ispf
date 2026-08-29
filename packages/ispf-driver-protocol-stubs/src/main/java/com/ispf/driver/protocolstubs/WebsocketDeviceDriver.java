package com.ispf.driver.protocolstubs;

/**
 * WebSocket protocol stub (websocket).
 * <p>
 * Generic WebSocket telemetry stub.
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
