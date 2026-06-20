package com.ispf.server.driver;

import com.ispf.driver.coap.CoapDeviceDriver;
import com.ispf.driver.http.HttpDeviceDriver;
import com.ispf.driver.icmp.IcmpDeviceDriver;
import com.ispf.driver.mqtt.MqttDeviceDriver;
import com.ispf.driver.modbus.ModbusTcpDeviceDriver;
import com.ispf.driver.snmp.SnmpDeviceDriver;
import com.ispf.driver.ssh.SshDeviceDriver;
import com.ispf.driver.virtual.VirtualDeviceDriver;
import org.springframework.stereotype.Component;

@Component
public class DriverFactory {

    public com.ispf.driver.DeviceDriver create(String driverId) {
        return switch (driverId) {
            case "virtual" -> new VirtualDeviceDriver();
            case "modbus-tcp" -> new ModbusTcpDeviceDriver();
            case "snmp" -> new SnmpDeviceDriver();
            case "mqtt" -> new MqttDeviceDriver();
            case "http" -> new HttpDeviceDriver();
            case "icmp" -> new IcmpDeviceDriver();
            case "ssh" -> new SshDeviceDriver();
            case "coap" -> new CoapDeviceDriver();
            default -> throw new IllegalArgumentException("Unknown driver: " + driverId);
        };
    }
}
