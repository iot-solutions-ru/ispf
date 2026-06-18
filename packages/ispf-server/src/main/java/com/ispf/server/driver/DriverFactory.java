package com.ispf.server.driver;

import com.ispf.driver.DeviceDriver;
import com.ispf.driver.modbus.ModbusTcpDeviceDriver;
import com.ispf.driver.mqtt.MqttDeviceDriver;
import com.ispf.driver.virtual.VirtualDeviceDriver;
import org.springframework.stereotype.Component;

@Component
public class DriverFactory {

    public DeviceDriver create(String driverId) {
        return switch (driverId) {
            case "virtual" -> new VirtualDeviceDriver();
            case "modbus-tcp" -> new ModbusTcpDeviceDriver();
            case "mqtt" -> new MqttDeviceDriver();
            default -> throw new IllegalArgumentException("Unknown driver: " + driverId);
        };
    }
}
