package com.ispf.driver.cameraai;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Camera AI edge protocol stub (camera-ai).
 * <p>
 * Edge vision/AI inference endpoint stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
