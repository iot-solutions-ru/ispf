package com.ispf.driver.protocolstubs;

/**
 * OPC Alarms and Events protocol stub (opc-ae).
 * <p>
 * OPC Classic A&E stub (DCOM/bridge required).
 */
public class OpcAeDeviceDriver extends ProtocolStubDeviceDriver {

    public OpcAeDeviceDriver() {
        super(
                "opc-ae",
                "OPC Alarms and Events Driver",
                "OPC Classic A&E stub (DCOM/bridge required)",
                135
        );
    }
}
