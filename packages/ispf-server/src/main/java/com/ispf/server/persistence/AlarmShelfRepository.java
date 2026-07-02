package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.AlarmShelfEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlarmShelfRepository extends JpaRepository<AlarmShelfEntity, String> {

    List<AlarmShelfEntity> findByActiveTrueOrderByShelvedAtDesc();

    @Query("""
            SELECT s FROM AlarmShelfEntity s
            WHERE s.active = TRUE
              AND s.objectPath = :objectPath
              AND s.eventName = :eventName
              AND (s.expiresAt IS NULL OR s.expiresAt > :now)
            """)
    Optional<AlarmShelfEntity> findActiveShelf(
            @Param("objectPath") String objectPath,
            @Param("eventName") String eventName,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
            UPDATE AlarmShelfEntity s
            SET s.active = FALSE
            WHERE s.active = TRUE
              AND s.expiresAt IS NOT NULL
              AND s.expiresAt <= :now
            """)
    int deactivateExpired(@Param("now") Instant now);
}
