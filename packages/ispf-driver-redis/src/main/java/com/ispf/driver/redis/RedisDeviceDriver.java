package com.ispf.driver.redis;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Redis protocol stub (redis).
 * <p>
 * Redis key/stream telemetry stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
