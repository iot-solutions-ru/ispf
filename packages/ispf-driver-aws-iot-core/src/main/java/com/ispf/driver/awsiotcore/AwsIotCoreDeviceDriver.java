package com.ispf.driver.awsiotcore;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * AWS IoT Core protocol stub (aws-iot-core).
 * <p>
 * AWS IoT Core MQTT/HTTP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class AwsIotCoreDeviceDriver extends ProtocolStubDeviceDriver {

    public AwsIotCoreDeviceDriver() {
        super(
                "aws-iot-core",
                "AWS IoT Core Driver",
                "AWS IoT Core MQTT/HTTP stub",
                8883
        );
    }
}
