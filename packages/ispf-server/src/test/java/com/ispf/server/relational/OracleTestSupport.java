package com.ispf.server.relational;

import org.springframework.util.ClassUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.oracle.OracleContainer;

import java.time.Duration;
import java.util.UUID;

/**
 * Shared helpers for ADR-0080 Oracle Testcontainers gates.
 * <p>Oracle Database Free ({@code gvenzl/oracle-free}, the 23ai line Oracle renamed 26ai) is the
 * Prod Yes (B1) cell by owner override; these gates say nothing about Standard Edition 2, Enterprise
 * Edition, 19c, or managed services. The ojdbc thin driver
 * comes from the opt-in {@code oracleLabJdbc} configuration ({@code -Dispf.test.oracleDocker=true});
 * ISPF never ships it, so these gates skip when it is absent.
 */
final class OracleTestSupport {

    static final String IMAGE = "gvenzl/oracle-free:23-slim-faststart";
    static final String DRIVER_CLASS = "oracle.jdbc.OracleDriver";
    static final String ISPF_USER = "ispf";

    private OracleTestSupport() {
    }

    static boolean available() {
        return ClassUtils.isPresent(DRIVER_CLASS, OracleTestSupport.class.getClassLoader()) && dockerAvailable();
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    @SuppressWarnings("resource")
    static OracleContainer newContainer() {
        return new OracleContainer(IMAGE)
                .withUsername(ISPF_USER)
                .withPassword("Ispf" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .withStartupTimeout(Duration.ofMinutes(8));
    }
}
