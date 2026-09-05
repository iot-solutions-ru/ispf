package com.ispf.driver.odata;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * OData protocol stub (odata).
 * <p>
 * OData v4 REST stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class OdataDeviceDriver extends ProtocolStubDeviceDriver {

    public OdataDeviceDriver() {
        super(
                "odata",
                "OData Driver",
                "OData v4 REST stub",
                80
        );
    }
}
