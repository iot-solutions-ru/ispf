package com.ispf.driver.protocolstubs;

/**
 * AWS IoT Core protocol stub (aws-iot-core).
 * <p>
 * AWS IoT Core MQTT/HTTP stub.
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
