package com.ispf.driver.weatherstation;

import com.ispf.driver.stubkit.ProtocolStubDeviceDriver;

/**
 * Weather station protocol stub (weather-station).
 * <p>
 * Davis/Vaisala-class weather station stub.
 * Clean-room ISPF stub, Apache-2.0 — no proprietary protocol stack.
 */
public class WeatherStationDeviceDriver extends ProtocolStubDeviceDriver {

    public WeatherStationDeviceDriver() {
        super(
                "weather-station",
                "Weather station Driver",
                "Davis/Vaisala-class weather station stub",
                22222
        );
    }
}
