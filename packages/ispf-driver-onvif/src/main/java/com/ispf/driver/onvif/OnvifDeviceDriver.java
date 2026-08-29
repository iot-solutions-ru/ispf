package com.ispf.driver.onvif;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * ONVIF protocol stub (onvif).
 * <p>
 * ONVIF Profile S/T device stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class OnvifDeviceDriver extends ProtocolStubDeviceDriver {

    public OnvifDeviceDriver() {
        super(
                "onvif",
                "ONVIF Driver",
                "ONVIF Profile S/T device stub",
                80
        );
    }
}
