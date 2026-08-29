package com.ispf.driver.opcae;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * OPC Alarms and Events protocol stub (opc-ae).
 * <p>
 * OPC Classic A&E stub (DCOM/bridge required).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
