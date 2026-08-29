package com.ispf.driver.foundationfieldbus;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Foundation Fieldbus protocol stub (foundation-fieldbus).
 * <p>
 * Foundation Fieldbus H1/HSE stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class FoundationFieldbusDeviceDriver extends ProtocolStubDeviceDriver {

    public FoundationFieldbusDeviceDriver() {
        super(
                "foundation-fieldbus",
                "Foundation Fieldbus Driver",
                "Foundation Fieldbus H1/HSE stub",
                1089
        );
    }
}
