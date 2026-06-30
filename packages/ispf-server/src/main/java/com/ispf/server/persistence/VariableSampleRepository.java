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

    @Query("""
            SELECT s FROM VariableSampleEntity s
            WHERE s.objectPath = :objectPath
              AND s.variableName = :variableName
              AND s.fieldName = :fieldName
              AND COALESCE(s.observedAt, s.sampledAt) >= :from
              AND COALESCE(s.observedAt, s.sampledAt) <= :to
            ORDER BY COALESCE(s.observedAt, s.sampledAt) ASC
            """)
    List<VariableSampleEntity> findByEffectiveTimestampBetweenOrderByEffectiveTimestampAsc(
            @Param("objectPath") String objectPath,
            @Param("variableName") String variableName,
            @Param("fieldName") String fieldName,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT s FROM VariableSampleEntity s
            WHERE s.objectPath = :objectPath
              AND s.variableName = :variableName
              AND s.fieldName = :fieldName
              AND COALESCE(s.observedAt, s.sampledAt) >= :from
              AND COALESCE(s.observedAt, s.sampledAt) <= :to
            ORDER BY COALESCE(s.observedAt, s.sampledAt) ASC
            """)
    List<VariableSampleEntity> findByEffectiveTimestampBetweenOrderByEffectiveTimestampAsc(
            @Param("objectPath") String objectPath,
            @Param("variableName") String variableName,
            @Param("fieldName") String fieldName,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
            SELECT s FROM VariableSampleEntity s
            WHERE s.objectPath = :objectPath
              AND s.variableName = :variableName
              AND s.fieldName = :fieldName
            ORDER BY COALESCE(s.observedAt, s.sampledAt) DESC
            """)
    List<VariableSampleEntity> findByObjectPathAndVariableNameAndFieldNameOrderByEffectiveTimestampDesc(
            String objectPath,
            String variableName,
            String fieldName,
            Pageable pageable
    );

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

    List<VariableSampleEntity> findByObjectPathAndVariableNameAndFieldNameAndSampledAtBetweenOrderBySampledAtAsc(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            Pageable pageable
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

    @Query(value = """
            SELECT *
            FROM (
                SELECT
                    to_timestamp(floor(extract(epoch from COALESCE(observed_at, sampled_at)) / :bucketSeconds) * :bucketSeconds)
                        AS bucket_start,
                    AVG(value_double) AS avg_val,
                    MIN(value_double) AS min_val,
                    MAX(value_double) AS max_val,
                    COUNT(*) AS sample_count
                FROM variable_samples
                WHERE object_path = :objectPath
                  AND variable_name = :variableName
                  AND field_name = :fieldName
                  AND COALESCE(observed_at, sampled_at) >= :fromTs
                  AND COALESCE(observed_at, sampled_at) <= :toTs
                  AND value_double IS NOT NULL
                GROUP BY 1
                ORDER BY 1 DESC
                LIMIT :maxBuckets
            ) recent_buckets
            ORDER BY bucket_start ASC
            """, nativeQuery = true)
    List<VariableSampleBucketAggregate> aggregateBuckets(
            @Param("objectPath") String objectPath,
            @Param("variableName") String variableName,
            @Param("fieldName") String fieldName,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            @Param("bucketSeconds") long bucketSeconds,
            @Param("maxBuckets") int maxBuckets
    );
}
