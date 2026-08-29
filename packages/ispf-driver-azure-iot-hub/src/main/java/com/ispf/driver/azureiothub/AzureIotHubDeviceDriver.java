package com.ispf.driver.azureiothub;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Azure IoT Hub protocol stub (azure-iot-hub).
 * <p>
 * Azure IoT Hub device/service stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
