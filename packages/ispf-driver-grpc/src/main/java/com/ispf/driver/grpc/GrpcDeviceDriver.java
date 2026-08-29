package com.ispf.driver.grpc;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * gRPC protocol stub (grpc).
 * <p>
 * Generic gRPC telemetry stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class GrpcDeviceDriver extends ProtocolStubDeviceDriver {

    public GrpcDeviceDriver() {
        super(
                "grpc",
                "gRPC Driver",
                "Generic gRPC telemetry stub",
                50051
        );
    }
}
