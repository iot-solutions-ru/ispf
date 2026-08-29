package com.ispf.driver.protocolstubs;

/**
 * Redis protocol stub (redis).
 * <p>
 * Redis key/stream telemetry stub.
 */
public class RedisDeviceDriver extends ProtocolStubDeviceDriver {

    public RedisDeviceDriver() {
        super(
                "redis",
                "Redis Driver",
                "Redis key/stream telemetry stub",
                6379
        );
    }
}
