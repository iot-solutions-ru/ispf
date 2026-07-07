package com.ispf.server.relational;

import com.ispf.server.config.MetadataDbProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.EnumMap;
import java.util.Map;

@Configuration
public class RelationalConfiguration {

    @Bean
    Map<RelationalDbKind, RelationalDialect> relationalDialectImplementations() {
        EnumMap<RelationalDbKind, RelationalDialect> dialects = new EnumMap<>(RelationalDbKind.class);
        dialects.put(RelationalDbKind.POSTGRESQL, new PostgreSqlDialect());
        dialects.put(RelationalDbKind.H2, new H2Dialect());
        dialects.put(RelationalDbKind.MSSQL, new MssqlDialect());
        dialects.put(RelationalDbKind.MYSQL, new MysqlDialect());
        dialects.put(RelationalDbKind.ORACLE, new OracleDialect());
        return Map.copyOf(dialects);
    }

    @Bean
    RelationalDialectDetector relationalDialectDetector(
            Map<RelationalDbKind, RelationalDialect> relationalDialectImplementations,
            MetadataDbProperties metadataDbProperties
    ) {
        return new RelationalDialectDetector(relationalDialectImplementations, metadataDbProperties);
    }

    @Bean
    RelationalDialect relationalDialect(DataSource dataSource, RelationalDialectDetector detector) {
        return detector.resolve(dataSource);
    }
}
