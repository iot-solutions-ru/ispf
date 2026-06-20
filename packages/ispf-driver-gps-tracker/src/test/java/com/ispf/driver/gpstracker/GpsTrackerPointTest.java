package com.ispf.driver.gpstracker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpsTrackerPointTest {

    @Test
    void parsesFeed() {
        GpsTrackerPoint point = GpsTrackerPoint.parse("feed");
        assertEquals(GpsTrackerPoint.GpsTrackerMode.FEED, point.mode());
    }
}
