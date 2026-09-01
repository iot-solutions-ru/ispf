package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBundleDeployImportSuccessTest {

    @Test
    void deployImportSucceededAcceptsOk() {
        assertTrue(AgentBundleDeploySuiteTest.deployImportSucceeded(Map.of("status", "OK")));
    }

    @Test
    void deployImportSucceededAcceptsPartialWithApplied() {
        assertTrue(AgentBundleDeploySuiteTest.deployImportSucceeded(Map.of(
                "status", "PARTIAL",
                "applied", List.of("register"),
                "errors", List.of("migrations: TIMESTAMPTZ")
        )));
    }

    @Test
    void deployImportSucceededRejectsPartialWithoutApplied() {
        assertFalse(AgentBundleDeploySuiteTest.deployImportSucceeded(Map.of(
                "status", "PARTIAL",
                "applied", List.of(),
                "errors", List.of("register: boom")
        )));
    }

    @Test
    void deployImportSucceededRejectsFailed() {
        assertFalse(AgentBundleDeploySuiteTest.deployImportSucceeded(Map.of(
                "status", "FAILED",
                "errors", List.of("register: boom")
        )));
    }
}
