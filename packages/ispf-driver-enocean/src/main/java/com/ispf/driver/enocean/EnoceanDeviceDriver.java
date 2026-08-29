package com.ispf.driver.enocean;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * EnOcean protocol stub (enocean).
 * <p>
 * EnOcean ESP3 / USB gateway stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class EnoceanDeviceDriver extends ProtocolStubDeviceDriver {

    public EnoceanDeviceDriver() {
        super(
                "enocean",
                "EnOcean Driver",
                "EnOcean ESP3 / USB gateway stub",
                54321
        );
    }
}
