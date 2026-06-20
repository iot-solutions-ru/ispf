package com.ispf.server.persistence;

import com.ispf.server.persistence.entity.VariableSampleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface VariableSampleRepository extends JpaRepository<VariableSampleEntity, Long> {

    List<VariableSampleEntity> findByObjectPathAndVariableNameAndFieldNameOrderBySampledAtDesc(
            String objectPath,
            String variableName,
            String fieldName,
            Pageable pageable
    );

    List<VariableSampleEntity> findByObjectPathAndVariableNameAndFieldNameAndSampledAtBetweenOrderBySampledAtAsc(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to
    );

    @Query("SELECT MIN(s.sampledAt) FROM VariableSampleEntity s")
    Instant findOldestSampledAt();

    @Query("SELECT MAX(s.sampledAt) FROM VariableSampleEntity s")
    Instant findNewestSampledAt();

    @Modifying
    @Query("DELETE FROM VariableSampleEntity s WHERE s.sampledAt < :cutoff")
    int deleteBySampledAtBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    int deleteByObjectPathAndVariableNameAndSampledAtBefore(
            String objectPath,
            String variableName,
            Instant cutoff
    );
}
