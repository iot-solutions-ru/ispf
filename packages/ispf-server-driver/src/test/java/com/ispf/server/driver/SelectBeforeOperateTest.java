package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectBeforeOperateTest {

    private static final String DEVICE = "root.platform.devices.valve-01";
    private static final String POINT = "cmdClose";

    @Test
    void operateSucceedsOnceBeforeTimeout() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        SelectBeforeOperate sbo = new SelectBeforeOperate(clock);

        sbo.arm(DEVICE, POINT, Duration.ofSeconds(30));
        assertDoesNotThrow(() -> sbo.operate(DEVICE, POINT));
        assertThrows(IllegalStateException.class, () -> sbo.operate(DEVICE, POINT));
    }

    @Test
    void operateFailsAfterTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        SelectBeforeOperate sbo = new SelectBeforeOperate(clock);

        sbo.arm(DEVICE, POINT, Duration.ofSeconds(5));
        clock.advance(Duration.ofSeconds(6));
        assertThrows(IllegalStateException.class, () -> sbo.operate(DEVICE, POINT));
    }

    @Test
    void operateWithoutArmFails() {
        SelectBeforeOperate sbo = new SelectBeforeOperate();
        assertThrows(IllegalStateException.class, () -> sbo.operate(DEVICE, POINT));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
