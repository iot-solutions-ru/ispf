package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
import com.ispf.driver.mqtt.MqttDeviceDriver;
import com.ispf.driver.modbus.ModbusTcpDeviceDriver;
import com.ispf.driver.virtual.VirtualDeviceDriver;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverCatalog {

    private final List<DriverMetadata> drivers = List.of(
            new VirtualDeviceDriver().metadata(),
            new MqttDeviceDriver().metadata(),
            new ModbusTcpDeviceDriver().metadata()
    );

    public List<DriverMetadata> list() {
        return drivers;
    }
}
