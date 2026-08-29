package com.ispf.driver.protocolstubs;

/**
 * Camera AI edge protocol stub (camera-ai).
 * <p>
 * Edge vision/AI inference endpoint stub.
 */
public class CameraAiDeviceDriver extends ProtocolStubDeviceDriver {

    public CameraAiDeviceDriver() {
        super(
                "camera-ai",
                "Camera AI edge Driver",
                "Edge vision/AI inference endpoint stub",
                8080
        );
    }
}
