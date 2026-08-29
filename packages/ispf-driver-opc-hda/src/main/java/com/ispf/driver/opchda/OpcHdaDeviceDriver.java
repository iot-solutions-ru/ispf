package com.ispf.driver.opchda;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * OPC Historical Data Access protocol stub (opc-hda).
 * <p>
 * OPC Classic HDA stub (DCOM/bridge required).
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
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
