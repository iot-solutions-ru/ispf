package com.ispf.driver.protocolstubs;

/**
 * gRPC protocol stub (grpc).
 * <p>
 * Generic gRPC telemetry stub.
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
