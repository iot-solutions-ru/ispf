package com.ispf.driver.protocolstubs;

/**
 * ONVIF protocol stub (onvif).
 * <p>
 * ONVIF Profile S/T device stub.
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
