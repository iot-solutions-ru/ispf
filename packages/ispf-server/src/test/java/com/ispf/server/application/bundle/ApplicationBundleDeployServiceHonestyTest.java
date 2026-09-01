package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationBundleDeployServiceHonestyTest {

    @Test
    void finalizeDeployStatusOkWhenNoErrors() {
        assertEquals("OK", ApplicationBundleDeployService.finalizeDeployStatus(List.of("register"), List.of()));
        assertEquals("OK", ApplicationBundleDeployService.finalizeDeployStatus(List.of(), List.of()));
        assertEquals("OK", ApplicationBundleDeployService.finalizeDeployStatus(null, null));
    }

    @Test
    void finalizeDeployStatusFailedWhenErrorsAndNothingApplied() {
        assertEquals(
                "FAILED",
                ApplicationBundleDeployService.finalizeDeployStatus(List.of(), List.of("register: boom"))
        );
        assertEquals(
                "FAILED",
                ApplicationBundleDeployService.finalizeDeployStatus(null, List.of("register: boom"))
        );
    }

    @Test
    void finalizeDeployStatusPartialWhenErrorsAndSomeApplied() {
        assertEquals(
                "PARTIAL",
                ApplicationBundleDeployService.finalizeDeployStatus(
                        List.of("register"),
                        List.of("dashboard:x: boom")
                )
        );
    }

    @Test
    void shouldActivateDeploySnapshotOnlyWhenNoErrors() {
        assertTrue(ApplicationBundleDeployService.shouldActivateDeploySnapshot(List.of()));
        assertTrue(ApplicationBundleDeployService.shouldActivateDeploySnapshot(null));
        assertFalse(ApplicationBundleDeployService.shouldActivateDeploySnapshot(
                List.of("applicationSync: boom")
        ));
        assertFalse(ApplicationBundleDeployService.shouldActivateDeploySnapshot(
                List.of("dashboard:x: boom")
        ));
    }

    @Test
    void partialOrFailedNeverActivatesEvenIfSomethingApplied() {
        assertEquals(
                "PARTIAL",
                ApplicationBundleDeployService.finalizeDeployStatus(
                        List.of("register", "applicationTree:root.applications.demo"),
                        List.of("applicationSync: boom")
                )
        );
        assertFalse(ApplicationBundleDeployService.shouldActivateDeploySnapshot(
                List.of("applicationSync: boom")
        ));

        assertEquals(
                "FAILED",
                ApplicationBundleDeployService.finalizeDeployStatus(
                        List.of(),
                        List.of("register: boom")
                )
        );
        assertFalse(ApplicationBundleDeployService.shouldActivateDeploySnapshot(
                List.of("register: boom")
        ));
    }
}
