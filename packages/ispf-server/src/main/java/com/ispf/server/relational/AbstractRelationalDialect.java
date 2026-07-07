package com.ispf.server.relational;

abstract class AbstractRelationalDialect implements RelationalDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier is required");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    protected static String bucketAggregateSqlPostgresStyle() {
        return """
                SELECT *
                FROM (
                    SELECT
                        to_timestamp(floor(extract(epoch from COALESCE(observed_at, sampled_at)) / ?) * ?)
                            AS bucket_start,
                        AVG(value_double) AS avg_val,
                        MIN(value_double) AS min_val,
                        MAX(value_double) AS max_val,
                        COUNT(*) AS sample_count
                    FROM variable_samples
                    WHERE object_path = ?
                      AND variable_name = ?
                      AND field_name = ?
                      AND COALESCE(observed_at, sampled_at) >= ?
                      AND COALESCE(observed_at, sampled_at) <= ?
                      AND value_double IS NOT NULL
                    GROUP BY 1
                    ORDER BY 1 DESC
                    LIMIT ?
                ) recent_buckets
                ORDER BY bucket_start ASC
                """;
    }
}
