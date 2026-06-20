package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
import com.ispf.driver.coap.CoapDeviceDriver;
import com.ispf.driver.http.HttpDeviceDriver;
import com.ispf.driver.icmp.IcmpDeviceDriver;
import com.ispf.driver.mqtt.MqttDeviceDriver;
import com.ispf.driver.modbus.ModbusTcpDeviceDriver;
import com.ispf.driver.snmp.SnmpDeviceDriver;
import com.ispf.driver.ssh.SshDeviceDriver;
import com.ispf.driver.virtual.VirtualDeviceDriver;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverCatalog {

    private final List<DriverMetadata> drivers = List.of(
            new VirtualDeviceDriver().metadata(),
            new MqttDeviceDriver().metadata(),
            new ModbusTcpDeviceDriver().metadata(),
            new SnmpDeviceDriver().metadata(),
            new HttpDeviceDriver().metadata(),
            new IcmpDeviceDriver().metadata(),
            new SshDeviceDriver().metadata(),
            new CoapDeviceDriver().metadata()
    );

    public List<DriverMetadata> list() {
        return drivers;
    }
}
