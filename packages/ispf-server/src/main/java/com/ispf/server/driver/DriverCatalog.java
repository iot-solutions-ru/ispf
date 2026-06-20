package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
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
            new CoapDeviceDriver().metadata(),
            new OpcUaDeviceDriver().metadata(),
            new S7DeviceDriver().metadata(),
            new Iec104DeviceDriver().metadata(),
            new BacnetDeviceDriver().metadata(),
            new Dnp3DeviceDriver().metadata(),
            new JmxDeviceDriver().metadata(),
            new JdbcDeviceDriver().metadata(),
            new FileDeviceDriver().metadata(),
            new FolderDeviceDriver().metadata(),
            new ApplicationDeviceDriver().metadata(),
            new MessageStreamDeviceDriver().metadata(),
            new NmeaDeviceDriver().metadata(),
            new TelnetDeviceDriver().metadata(),
            new SoapDeviceDriver().metadata(),
            new IpHostDeviceDriver().metadata(),
            new KafkaDeviceDriver().metadata(),
            new GpsTrackerDeviceDriver().metadata(),
            new FlexibleDeviceDriver().metadata(),
            new MbusDeviceDriver().metadata(),
            new OmronFinsDeviceDriver().metadata(),
            new AsteriskDeviceDriver().metadata(),
            new SmppDeviceDriver().metadata(),
            new SmbDeviceDriver().metadata()
    );

    public List<DriverMetadata> list() {
        return drivers;
    }
}
