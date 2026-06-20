package com.ispf.server.driver;

import com.ispf.driver.application.ApplicationDeviceDriver;
import com.ispf.driver.asterisk.AsteriskDeviceDriver;
import com.ispf.driver.bacnet.BacnetDeviceDriver;
import com.ispf.driver.dnp3.Dnp3DeviceDriver;
import com.ispf.driver.coap.CoapDeviceDriver;
import com.ispf.driver.file.FileDeviceDriver;
import com.ispf.driver.flexible.FlexibleDeviceDriver;
import com.ispf.driver.gpstracker.GpsTrackerDeviceDriver;
import com.ispf.driver.folder.FolderDeviceDriver;
import com.ispf.driver.http.HttpDeviceDriver;
import com.ispf.driver.icmp.IcmpDeviceDriver;
import com.ispf.driver.iec104.Iec104DeviceDriver;
import com.ispf.driver.iphost.IpHostDeviceDriver;
import com.ispf.driver.jdbc.JdbcDeviceDriver;
import com.ispf.driver.kafka.KafkaDeviceDriver;
import com.ispf.driver.jmx.JmxDeviceDriver;
import com.ispf.driver.messagestream.MessageStreamDeviceDriver;
import com.ispf.driver.mbus.MbusDeviceDriver;
import com.ispf.driver.mqtt.MqttDeviceDriver;
import com.ispf.driver.modbus.ModbusTcpDeviceDriver;
import com.ispf.driver.nmea.NmeaDeviceDriver;
import com.ispf.driver.omronfins.OmronFinsDeviceDriver;
import com.ispf.driver.opcua.OpcUaDeviceDriver;
import com.ispf.driver.s7.S7DeviceDriver;
import com.ispf.driver.smb.SmbDeviceDriver;
import com.ispf.driver.smpp.SmppDeviceDriver;
import com.ispf.driver.snmp.SnmpDeviceDriver;
import com.ispf.driver.soap.SoapDeviceDriver;
import com.ispf.driver.ssh.SshDeviceDriver;
import com.ispf.driver.telnet.TelnetDeviceDriver;
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
            case "opcua" -> new OpcUaDeviceDriver();
            case "s7" -> new S7DeviceDriver();
            case "iec104" -> new Iec104DeviceDriver();
            case "bacnet" -> new BacnetDeviceDriver();
            case "dnp3" -> new Dnp3DeviceDriver();
            case "jmx" -> new JmxDeviceDriver();
            case "jdbc" -> new JdbcDeviceDriver();
            case "file" -> new FileDeviceDriver();
            case "folder" -> new FolderDeviceDriver();
            case "application" -> new ApplicationDeviceDriver();
            case "message-stream" -> new MessageStreamDeviceDriver();
            case "nmea" -> new NmeaDeviceDriver();
            case "telnet" -> new TelnetDeviceDriver();
            case "soap" -> new SoapDeviceDriver();
            case "ip-host" -> new IpHostDeviceDriver();
            case "kafka" -> new KafkaDeviceDriver();
            case "gps-tracker" -> new GpsTrackerDeviceDriver();
            case "flexible" -> new FlexibleDeviceDriver();
            case "mbus" -> new MbusDeviceDriver();
            case "omron-fins" -> new OmronFinsDeviceDriver();
            case "asterisk" -> new AsteriskDeviceDriver();
            case "smpp" -> new SmppDeviceDriver();
            case "smb" -> new SmbDeviceDriver();
            default -> throw new IllegalArgumentException("Unknown driver: " + driverId);
        };
    }
}
