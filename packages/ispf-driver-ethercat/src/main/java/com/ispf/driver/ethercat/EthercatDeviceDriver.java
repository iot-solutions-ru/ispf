package com.ispf.driver.ethercat;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * EtherCAT protocol stub (ethercat).
 * <p>
 * EtherCAT master/gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
