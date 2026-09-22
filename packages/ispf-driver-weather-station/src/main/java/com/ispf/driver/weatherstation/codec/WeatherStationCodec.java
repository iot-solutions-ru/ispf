package com.ispf.driver.weatherstation.codec;

/**
 * Davis Vantage LOOP command over TCP.
 * <p>
 * Wire command is exactly {@code LOOP\n} ({@code 4C 4F 4F 50 0A}). Console ACKs with
 * {@code 0x06} then a LOOP payload. This codec does not assemble the full 99-byte Vantage
 * packet and is not a Vaisala sensor dialect.
 */
public final class WeatherStationCodec {

    public static final int ACK = 0x06;

    private WeatherStationCodec() {
    }

    /** Davis Vantage LOOP command: {@code L O O P LF}. */
    public static byte[] encodeLoopCommand() {
        return new byte[] { 0x4C, 0x4F, 0x4F, 0x50, 0x0A };
    }
}
