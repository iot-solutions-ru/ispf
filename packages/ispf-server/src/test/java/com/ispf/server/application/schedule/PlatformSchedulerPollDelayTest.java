package com.ispf.server.application.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSchedulerPollDelayTest {

    @Test
    void defaultPollDelayAllowsOneSecondSchedules() {
        assertThat(PlatformSchedulerService.DEFAULT_POLL_DELAY_MS)
                .isEqualTo(1_000L)
                .isLessThan(5_000L);
    }
}
