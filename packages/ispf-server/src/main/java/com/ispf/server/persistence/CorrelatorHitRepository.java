package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.CorrelatorHitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CorrelatorHitRepository extends JpaRepository<CorrelatorHitEntity, Long> {

    long countByCorrelatorIdAndObjectPathAndOccurredAtAfter(
            String correlatorId,
            String objectPath,
            Instant since
    );

    @Modifying
    @Query("DELETE FROM CorrelatorHitEntity h WHERE h.correlatorId = :correlatorId")
    void deleteByCorrelatorId(@Param("correlatorId") String correlatorId);

    @Modifying
    @Query("DELETE FROM CorrelatorHitEntity h WHERE h.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
