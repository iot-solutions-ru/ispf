package com.ispf.driver.protocolstubs;

/**
 * Weather station protocol stub (weather-station).
 * <p>
 * Davis/Vaisala-class weather station stub.
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
