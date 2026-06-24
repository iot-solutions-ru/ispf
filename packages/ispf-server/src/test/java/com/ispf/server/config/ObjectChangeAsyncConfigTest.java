package com.ispf.server.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectChangeAsyncConfigTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = "ispf.object-change.async-enabled=true")
    class WhenAsyncEnabled {

        @Autowired
        @Qualifier("objectChangeExecutor")
        private Executor objectChangeExecutor;

        @Test
        void registersConfiguredExecutor() {
            assertThat(objectChangeExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) objectChangeExecutor;
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(8);
            assertThat(executor.getQueueCapacity()).isEqualTo(10_000);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("object-change-");
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = "ispf.object-change.async-enabled=false")
    class WhenAsyncDisabled {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void doesNotRegisterExecutor() {
            assertThatThrownBy(() -> applicationContext.getBean("objectChangeExecutor"))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }
}
