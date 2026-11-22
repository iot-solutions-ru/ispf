package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OracleJdbcDriverEnvironmentPostProcessorTest {

    private final OracleJdbcDriverEnvironmentPostProcessor processor = new OracleJdbcDriverEnvironmentPostProcessor();

    @Test
    void detectsOracleFromKindOrFromUrlWhenKindIsEmpty() {
        assertThat(OracleJdbcDriverEnvironmentPostProcessor.isOracleMetadata(
                new MockEnvironment().withProperty("ispf.metadata.db.kind", "oracle"))).isTrue();
        assertThat(OracleJdbcDriverEnvironmentPostProcessor.isOracleMetadata(
                new MockEnvironment().withProperty("spring.datasource.url", "JDBC:ORACLE:thin:@//db:1521/ISPF"))).isTrue();
        assertThat(OracleJdbcDriverEnvironmentPostProcessor.isOracleMetadata(new MockEnvironment()
                .withProperty("ispf.metadata.db.kind", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:oracle:thin:@//db:1521/ISPF"))).isFalse();
        assertThat(OracleJdbcDriverEnvironmentPostProcessor.isOracleMetadata(
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db/ispf"))).isFalse();
    }

    @Test
    void refusesOracleBeforeTheDataSourceWhenTheDriverIsMissing() {
        SpringApplication application = applicationWithout("oracle.jdbc.");
        MockEnvironment environment = new MockEnvironment().withProperty("ispf.metadata.db.kind", "oracle");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oracle.jdbc.OracleDriver")
                .hasMessageContaining("does not ship")
                .hasMessageContaining("PropertiesLauncher");
    }

    @Test
    void leavesOtherEnginesAlone() {
        SpringApplication application = applicationWithout("oracle.jdbc.");
        MockEnvironment environment = new MockEnvironment().withProperty("ispf.metadata.db.kind", "postgresql");
        assertThatCode(() -> processor.postProcessEnvironment(environment, application)).doesNotThrowAnyException();
    }

    private static SpringApplication applicationWithout(String hiddenPrefix) {
        ClassLoader parent = OracleJdbcDriverEnvironmentPostProcessorTest.class.getClassLoader();
        ClassLoader hiding = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(hiddenPrefix)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        SpringApplication application = new SpringApplication();
        application.setResourceLoader(new DefaultResourceLoader(hiding));
        return application;
    }
}
