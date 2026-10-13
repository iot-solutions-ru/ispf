package com.ispf.server.platform;

import com.ispf.server.spi.LeaderLock;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * JDBC lease table for cluster singleton jobs.
 * <p>
 * Lease times come from replica clocks. The holder renews every {@code ttl / 3}, so another replica can take
 * a renewed lease only if its clock runs ahead by more than {@code 2/3 ttl}. Lease SQL runs outside the
 * caller's transaction: other replicas must see the row at once, and the renewer must never wait on a row
 * locked by a long job transaction.
 */
@Service
public class PlatformLeaderLockService implements LeaderLock {

    private static final Logger log = LoggerFactory.getLogger(PlatformLeaderLockService.class);
    private static final long RENEW_POLL_MS = 1_000L;
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate outsideCallerTransaction;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final String instanceId = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, Lease> held = new ConcurrentHashMap<>();
    private final AtomicLong lastRenewFailureWarnAt;
    private ScheduledExecutorService renewer;

    @Autowired
    public PlatformLeaderLockService(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this(jdbcTemplate, transactionManager, Clock.systemUTC(), System::nanoTime);
    }

    PlatformLeaderLockService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock,
            LongSupplier nanoTime
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.outsideCallerTransaction = new TransactionTemplate(transactionManager);
        this.outsideCallerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.clock = clock;
        this.nanoTime = nanoTime;
        this.lastRenewFailureWarnAt = new AtomicLong(nanoTime.getAsLong() - WARN_INTERVAL_NANOS);
    }

    @PostConstruct
    void startRenewer() {
        renewer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "leader-lock-renewer");
            thread.setDaemon(true);
            return thread;
        });
        // scheduleWithFixedDelay drops the schedule after the first uncaught exception.
        var unused = renewer.scheduleWithFixedDelay(() -> {
            try {
                renewHeldLeases();
            } catch (RuntimeException ex) {
                log.warn("Leader lock renewal round failed: {}", ex.getMessage());
            }
        }, RENEW_POLL_MS, RENEW_POLL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (renewer != null) {
            renewer.shutdownNow();
        }
        for (String lockName : List.copyOf(held.keySet())) {
            try {
                release(lockName);
            } catch (RuntimeException ex) {
                log.debug("Leader lock {} not released on shutdown: {}", lockName, ex.getMessage());
            }
        }
    }

    @Override
    public boolean runIfLeader(String lockName, Duration ttl, Runnable task) {
        long startedAt = nanoTime.getAsLong();
        if (!takeOrRenew(lockName, ttl)) {
            held.remove(lockName);
            return false;
        }
        held.compute(lockName, (name, lease) -> lease == null
                ? new Lease(ttl, startedAt, 1, startedAt)
                : new Lease(ttl, startedAt, lease.running() + 1, lease.idleSinceNanos()));
        try {
            task.run();
        } finally {
            long now = nanoTime.getAsLong();
            held.computeIfPresent(lockName, (name, lease) -> lease.finished(now));
        }
        return true;
    }

    @Override
    public boolean isHeld(String lockName) {
        Lease lease = held.get(lockName);
        return lease != null && lease.validAt(nanoTime.getAsLong());
    }

    @Override
    public boolean tryAcquire(String lockName, Duration ttl) {
        return takeOrRenew(lockName, ttl);
    }

    @Override
    public void release(String lockName) {
        held.remove(lockName);
        outsideCallerTransaction.executeWithoutResult(status -> jdbcTemplate.update(
                """
                        DELETE FROM platform_leader_locks
                        WHERE lock_name = ? AND holder_id = ?
                        """,
                lockName,
                instanceId
        ));
    }

    void renewHeldLeases() {
        long now = nanoTime.getAsLong();
        held.forEach((lockName, lease) -> {
            if (lease.lapsedAt(now)) {
                held.remove(lockName, lease);
            } else if (lease.renewalDueAt(now)) {
                renew(lockName, lease);
            }
        });
    }

    private boolean takeOrRenew(String lockName, Duration ttl) {
        Instant now = clock.instant();
        Timestamp expiresAt = Timestamp.from(now.plus(ttl));
        Boolean taken = outsideCallerTransaction.execute(status -> {
            int updated = jdbcTemplate.update(
                    """
                            UPDATE platform_leader_locks
                            SET holder_id = ?, expires_at = ?
                            WHERE lock_name = ? AND (holder_id = ? OR expires_at <= ?)
                            """,
                    instanceId,
                    expiresAt,
                    lockName,
                    instanceId,
                    Timestamp.from(now)
            );
            if (updated > 0) {
                return true;
            }
            try {
                jdbcTemplate.update(
                        """
                                INSERT INTO platform_leader_locks (lock_name, holder_id, expires_at)
                                VALUES (?, ?, ?)
                                """,
                        lockName,
                        instanceId,
                        expiresAt
                );
                return true;
            } catch (DataIntegrityViolationException ex) {
                return false;
            }
        });
        return Boolean.TRUE.equals(taken);
    }

    private void renew(String lockName, Lease lease) {
        long startedAt = nanoTime.getAsLong();
        int updated;
        try {
            updated = jdbcTemplate.update(
                    """
                            UPDATE platform_leader_locks
                            SET expires_at = ?
                            WHERE lock_name = ? AND holder_id = ?
                            """,
                    Timestamp.from(clock.instant().plus(lease.ttl())),
                    lockName,
                    instanceId
            );
        } catch (RuntimeException ex) {
            warnRenewFailure(lockName, ex);
            return;
        }
        if (updated == 0) {
            if (held.remove(lockName, lease)) {
                log.warn("Leader lock {} was taken over by another replica", lockName);
            }
            return;
        }
        held.computeIfPresent(lockName, (name, current) -> current.renewed(startedAt));
    }

    private void warnRenewFailure(String lockName, RuntimeException ex) {
        long now = nanoTime.getAsLong();
        long last = lastRenewFailureWarnAt.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastRenewFailureWarnAt.compareAndSet(last, now)) {
            log.warn("Failed to renew leader lock {}: {}", lockName, ex.getMessage());
        }
    }

    /** Ticks keep the lease while {@code running > 0} or until it has been idle for {@code ttl}. */
    private record Lease(Duration ttl, long renewedAtNanos, int running, long idleSinceNanos) {

        boolean validAt(long now) {
            return now - renewedAtNanos < ttl.toNanos() * 2 / 3;
        }

        boolean lapsedAt(long now) {
            return running == 0 && now - idleSinceNanos >= ttl.toNanos();
        }

        boolean renewalDueAt(long now) {
            return now - renewedAtNanos >= ttl.toNanos() / 3;
        }

        Lease finished(long now) {
            return new Lease(ttl, renewedAtNanos, running - 1, running == 1 ? now : idleSinceNanos);
        }

        Lease renewed(long now) {
            return new Lease(ttl, now, running, idleSinceNanos);
        }
    }
}
