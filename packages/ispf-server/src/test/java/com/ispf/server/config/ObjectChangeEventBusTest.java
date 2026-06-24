package com.ispf.server.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectChangeEventBusTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = "ispf.object-change.async-enabled=true")
    class WhenAsyncEnabled {

        @Autowired
        private com.ispf.server.object.bus.ObjectChangeEventBus eventBus;

        @Test
        void registersEventBus() {
            assertThat(eventBus).isNotNull();
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = "ispf.object-change.async-enabled=false")
    class WhenAsyncDisabled {

        @Autowired
        private com.ispf.server.object.bus.ObjectChangeEventBus eventBus;

        @Test
        void stillRegistersEventBusForSyncDispatch() {
            assertThat(eventBus).isNotNull();
        }
    }
}
