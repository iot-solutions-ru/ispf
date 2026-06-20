package com.ispf.driver.cwmp;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CwmpPointTest {

    @Test
    void parsesParameterName() {
        CwmpPoint point = CwmpPoint.parse("Device.DeviceInfo.SoftwareVersion");
        assertEquals("Device.DeviceInfo.SoftwareVersion", point.parameterName());
        assertTrue(!point.isConnectedPoint());
    }

    @Test
    void parsesConnectedStatus() {
        CwmpPoint point = CwmpPoint.parse("connected");
        assertTrue(point.isConnectedPoint());
        assertEquals("connected", point.parameterName());
    }

    @Test
    void parsesParametersFromInformResponse() {
        String xml = """
                <ParameterList>
                  <ParameterValueStruct>
                    <Name>Device.DeviceInfo.SoftwareVersion</Name>
                    <Value>2.1.0</Value>
                  </ParameterValueStruct>
                </ParameterList>
                """;
        Map<String, String> params = new HashMap<>();
        CwmpDeviceDriver.parseParameters(xml, params);
        assertEquals("2.1.0", params.get("Device.DeviceInfo.SoftwareVersion"));
    }

    @Test
    void parsesInformParametersConfig() {
        assertEquals(
                java.util.List.of("Device.DeviceInfo.SoftwareVersion", "Device.DeviceInfo.HardwareVersion"),
                CwmpDeviceDriver.parseInformParameters(
                        "Device.DeviceInfo.SoftwareVersion, Device.DeviceInfo.HardwareVersion"
                )
        );
    }

    @Test
    void extractsGetParameterNamesFromAcsRpc() {
        String xml = """
                <GetParameterValues>
                  <ParameterNames soap-enc:arrayType="xsd:string[1]">
                    <string>Device.DeviceInfo.SerialNumber</string>
                  </ParameterNames>
                </GetParameterValues>
                """;
        assertEquals(
                java.util.List.of("Device.DeviceInfo.SerialNumber"),
                CwmpDeviceDriver.extractGetParameterNames(xml)
        );
    }
}
