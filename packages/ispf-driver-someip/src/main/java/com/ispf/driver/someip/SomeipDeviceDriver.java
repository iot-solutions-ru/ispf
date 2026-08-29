package com.ispf.driver.someip;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * SOME/IP protocol stub (someip).
 * <p>
 * AUTOSAR SOME/IP stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class SomeipDeviceDriver extends ProtocolStubDeviceDriver {

    public SomeipDeviceDriver() {
        super(
                "someip",
                "SOME/IP Driver",
                "AUTOSAR SOME/IP stub",
                30490
        );
    }
}
