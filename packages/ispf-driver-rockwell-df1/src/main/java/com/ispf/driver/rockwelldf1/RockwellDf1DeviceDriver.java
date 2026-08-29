package com.ispf.driver.rockwelldf1;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Rockwell DF1 protocol stub (rockwell-df1).
 * <p>
 * Allen-Bradley DF1 serial/TCP bridge stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class RockwellDf1DeviceDriver extends ProtocolStubDeviceDriver {

    public RockwellDf1DeviceDriver() {
        super(
                "rockwell-df1",
                "Rockwell DF1 Driver",
                "Allen-Bradley DF1 serial/TCP bridge stub",
                2222
        );
    }
}
