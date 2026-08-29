package com.ispf.driver.protocolstubs;

/**
 * EtherCAT protocol stub (ethercat).
 * <p>
 * EtherCAT master/gateway stub.
 */
public class EthercatDeviceDriver extends ProtocolStubDeviceDriver {

    public EthercatDeviceDriver() {
        super(
                "ethercat",
                "EtherCAT Driver",
                "EtherCAT master/gateway stub",
                34980
        );
    }
}
