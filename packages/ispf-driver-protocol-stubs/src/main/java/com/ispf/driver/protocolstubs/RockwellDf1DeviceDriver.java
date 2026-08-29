package com.ispf.driver.protocolstubs;

/**
 * Rockwell DF1 protocol stub (rockwell-df1).
 * <p>
 * Allen-Bradley DF1 serial/TCP bridge stub.
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
