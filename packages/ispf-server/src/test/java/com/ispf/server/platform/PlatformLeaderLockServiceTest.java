package com.ispf.server.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformLeaderLockServiceTest {

    private static final String LOCK = "test_singleton_job";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final Duration TICK = Duration.ofSeconds(5);

    private final SteppingClock clock = new SteppingClock();
    private final AtomicLong nanos = new AtomicLong();
    private JdbcTemplate jdbcTemplate;
    private DataSourceTransactionManager transactionManager;
    private PlatformLeaderLockService replicaA;
    private PlatformLeaderLockService replicaB;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:leader-lock-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE platform_leader_locks (
                    lock_name   VARCHAR(128) PRIMARY KEY,
                    holder_id   VARCHAR(64)  NOT NULL,
                    expires_at  TIMESTAMP    NOT NULL
                )
                """);
        transactionManager = new DataSourceTransactionManager(dataSource);
        replicaA = newReplica();
        replicaB = newReplica();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SHUTDOWN");
    }

    @Test
    void leaseStaysWithTheHolderAcrossTicks() {
        for (Duration elapsed = Duration.ZERO; elapsed.compareTo(TTL.multipliedBy(2)) <= 0; elapsed = elapsed.plus(TICK)) {
            assertThat(replicaA.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)).as("holder at %s", elapsed).isTrue();
            assertThat(replicaB.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)).as("follower at %s", elapsed).isFalse();
            elapse(TICK);
            replicaA.renewHeldLeases();
        }
    }

    @Test
    void holderRenewsWhileTheTaskRunsPastTheTtl() {
        AtomicBoolean followerRan = new AtomicBoolean();

        boolean ran = replicaA.runIfLeader(LOCK, TTL, () -> {
            for (Duration elapsed = Duration.ZERO; elapsed.compareTo(TTL.multipliedBy(3)) < 0; elapsed = elapsed.plus(TICK)) {
                elapse(TICK);
                replicaA.renewHeldLeases();
                assertThat(replicaA.isHeld(LOCK)).as("held at %s", elapsed).isTrue();
                followerRan.compareAndSet(false, replicaB.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop));
            }
        });

        assertThat(ran).isTrue();
        assertThat(followerRan).isFalse();
    }

    @Test
    void overlappingTicksKeepTheLeaseUntilTheLastOneFinishes() {
        AtomicBoolean followerRan = new AtomicBoolean();

        replicaA.runIfLeader(LOCK, TTL, () -> {
            replicaA.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop);
            for (Duration elapsed = Duration.ZERO; elapsed.compareTo(TTL.multipliedBy(3)) < 0; elapsed = elapsed.plus(TICK)) {
                elapse(TICK);
                replicaA.renewHeldLeases();
                followerRan.compareAndSet(false, replicaB.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop));
            }
        });

        assertThat(followerRan).isFalse();
    }

    @Test
    void idleLeaseLapsesSoAnotherReplicaTakesOver() {
        assertThat(replicaA.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)).isTrue();

        Duration idle = Duration.ZERO;
        while (!replicaB.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)) {
            assertThat(idle).as("handover after the holder stops ticking").isLessThan(TTL.multipliedBy(2));
            elapse(TICK);
            idle = idle.plus(TICK);
            replicaA.renewHeldLeases();
        }

        assertThat(idle).as("idle grace keeps the lease sticky between ticks").isGreaterThanOrEqualTo(TTL);
        assertThat(replicaA.isHeld(LOCK)).isFalse();
    }

    @Test
    void isHeldTurnsFalseBeforeTheLeaseCanBeTakenWhenRenewalStalls() {
        AtomicBoolean followerTookOver = new AtomicBoolean();

        replicaA.runIfLeader(LOCK, TTL, () -> {
            assertThat(replicaA.isHeld(LOCK)).isTrue();
            elapse(TTL.multipliedBy(2).dividedBy(3));
            assertThat(replicaA.isHeld(LOCK)).isFalse();
            followerTookOver.set(replicaB.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop));
        });

        assertThat(followerTookOver).isFalse();
    }

    @Test
    void takenOverLeaseIsDroppedOnRenewal() {
        replicaA.runIfLeader(LOCK, TTL, () -> {
            jdbcTemplate.update(
                    "UPDATE platform_leader_locks SET holder_id = ? WHERE lock_name = ?",
                    "other-replica",
                    LOCK
            );
            elapse(TTL.dividedBy(3));
            replicaA.renewHeldLeases();
            assertThat(replicaA.isHeld(LOCK)).isFalse();
        });

        assertThat(holder()).isEqualTo("other-replica");
    }

    @Test
    void shutdownHandsTheLeaseOverAtOnce() {
        assertThat(replicaA.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)).isTrue();

        replicaA.shutdown();

        assertThat(replicaB.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)).isTrue();
    }

    @Test
    void leaseCommitsEvenWhenTheCallerTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(replicaA.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)).isTrue();
            status.setRollbackOnly();
        });

        assertThat(replicaB.runIfLeader(LOCK, TTL, PlatformLeaderLockServiceTest::noop)).isFalse();
    }

    @Test
    void tryAcquireLeaseIsNotRenewedInTheBackground() {
        assertThat(replicaA.tryAcquire(LOCK, TTL)).isTrue();
        elapse(TTL.minus(TICK));
        replicaA.renewHeldLeases();
        elapse(TICK);

        assertThat(replicaB.tryAcquire(LOCK, TTL)).isTrue();
    }

    private static void noop() {
    }

    private PlatformLeaderLockService newReplica() {
        return new PlatformLeaderLockService(jdbcTemplate, transactionManager, clock, nanos::get);
    }

    private void elapse(Duration step) {
        clock.advance(step);
        nanos.addAndGet(step.toNanos());
    }

    private String holder() {
        return jdbcTemplate.queryForObject(
                "SELECT holder_id FROM platform_leader_locks WHERE lock_name = ?",
                String.class,
                LOCK
        );
    }

    private static final class SteppingClock extends Clock {

        private Instant now = Instant.parse("2026-10-09T10:00:00Z");

        void advance(Duration step) {
            now = now.plus(step);
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
            return now;
        }
    }
}
