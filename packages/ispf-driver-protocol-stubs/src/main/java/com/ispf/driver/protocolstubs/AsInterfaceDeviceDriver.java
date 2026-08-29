package com.ispf.driver.protocolstubs;

/**
 * AS-Interface protocol stub (as-interface).
 * <p>
 * AS-Interface master/gateway stub.
 */
public class AsInterfaceDeviceDriver extends ProtocolStubDeviceDriver {

    public AsInterfaceDeviceDriver() {
        super(
                "as-interface",
                "AS-Interface Driver",
                "AS-Interface master/gateway stub",
                9600
        );
    }
}
