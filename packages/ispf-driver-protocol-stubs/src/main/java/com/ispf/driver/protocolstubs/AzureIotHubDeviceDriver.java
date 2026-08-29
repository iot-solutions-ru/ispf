package com.ispf.driver.protocolstubs;

/**
 * Azure IoT Hub protocol stub (azure-iot-hub).
 * <p>
 * Azure IoT Hub device/service stub.
 */
public class AzureIotHubDeviceDriver extends ProtocolStubDeviceDriver {

    public AzureIotHubDeviceDriver() {
        super(
                "azure-iot-hub",
                "Azure IoT Hub Driver",
                "Azure IoT Hub device/service stub",
                8883
        );
    }
}
