package com.ispf.driver.protocolstubs;

/**
 * OPC Historical Data Access protocol stub (opc-hda).
 * <p>
 * OPC Classic HDA stub (DCOM/bridge required).
 */
public class OpcHdaDeviceDriver extends ProtocolStubDeviceDriver {

    public OpcHdaDeviceDriver() {
        super(
                "opc-hda",
                "OPC Historical Data Access Driver",
                "OPC Classic HDA stub (DCOM/bridge required)",
                135
        );
    }
}
