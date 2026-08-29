package com.ispf.driver.rtsp;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * RTSP protocol stub (rtsp).
 * <p>
 * RTSP media/metadata stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class RtspDeviceDriver extends ProtocolStubDeviceDriver {

    public RtspDeviceDriver() {
        super(
                "rtsp",
                "RTSP Driver",
                "RTSP media/metadata stub",
                554
        );
    }
}
