package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.server.config.BindingProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BindingRuleAsyncExecutorTest {

    @Test
    void coalescesBurstSubmissionsPerBindingWhenEnabled() throws Exception {
        BindingProperties properties = new BindingProperties();
        properties.setAsyncCoalesceEnabled(true);
        BindingRuleAsyncExecutor executor = new BindingRuleAsyncExecutor(properties);
        BindingRule rule = sampleRule();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        for (int i = 0; i < 100; i++) {
            executor.schedule("root.test.device", rule, () -> {
                runs.incrementAndGet();
                done.countDown();
            });
        }

        assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(runs.get()).isLessThan(100);
        assertThat(runs.get()).isGreaterThanOrEqualTo(1);
        executor.shutdown();
    }

    @Test
    void processesEverySubmissionWhenCoalesceDisabled() throws Exception {
        BindingProperties properties = new BindingProperties();
        properties.setAsyncCoalesceEnabled(false);
        BindingRuleAsyncExecutor executor = new BindingRuleAsyncExecutor(properties);
        BindingRule rule = sampleRule();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            executor.schedule("root.test.device", rule, () -> {
                runs.incrementAndGet();
                done.countDown();
            });
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runs.get()).isEqualTo(100);
        executor.shutdown();
    }

    @Test
    void defaultDoesNotCoalesce() throws Exception {
        BindingRuleAsyncExecutor executor = new BindingRuleAsyncExecutor(new BindingProperties());
        BindingRule rule = sampleRule();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(50);

        for (int i = 0; i < 50; i++) {
            executor.schedule("root.test.device", rule, () -> {
                runs.incrementAndGet();
                done.countDown();
            });
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(runs.get()).isEqualTo(50);
        executor.shutdown();
    }

    private static BindingRule sampleRule() {
        return new BindingRule(
                "test-rule",
                "Test",
                true,
                0,
                null,
                "",
                "true",
                new BindingTarget("value", "value")
        );
    }
}
