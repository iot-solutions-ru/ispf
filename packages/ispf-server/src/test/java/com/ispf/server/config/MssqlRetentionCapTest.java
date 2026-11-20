package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MssqlRetentionCapTest {

    @Test
    void maxRetentionDaysDefaultsToNinetyAndRejectsZero() {
        MssqlRelationalProperties props = new MssqlRelationalProperties();
        assertThat(props.getMaxRetentionDays()).isEqualTo(90);
        props.setMaxRetentionDays(0);
        assertThat(props.getMaxRetentionDays()).isEqualTo(1);
        props.setMaxRetentionDays(30);
        assertThat(props.getMaxRetentionDays()).isEqualTo(30);
    }
}
