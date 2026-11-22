package com.ispf.server.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/**
 * ADR-0080: an Oracle ISPF_DB needs the operator-supplied Oracle JDBC driver. Spring Boot loads the
 * driver while it builds the DataSource, before any boot guard runs, so check here and fail with the
 * remedy instead of a bare "Cannot load driver class".
 */
public final class OracleJdbcDriverEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ClassLoader classLoader = application.getClassLoader();
        if (isOracleMetadata(environment)
                && !ClassUtils.isPresent(MetadataDialectBootPolicy.ORACLE_JDBC_DRIVER, classLoader)) {
            throw new IllegalStateException(MetadataDialectBootPolicy.oracleDriverMissingMessage());
        }
    }

    /** {@code ISPF_METADATA_DB_KIND=oracle}, or no kind and an Oracle {@code spring.datasource.url}. */
    public static boolean isOracleMetadata(Environment environment) {
        String kind = environment.getProperty("ispf.metadata.db.kind", "").trim();
        if (!kind.isEmpty()) {
            return kind.equalsIgnoreCase("oracle");
        }
        String url = environment.getProperty("spring.datasource.url", "").trim();
        return url.regionMatches(true, 0, "jdbc:oracle:", 0, "jdbc:oracle:".length());
    }
}
