package com.ispf.server.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardTemplateTest {

    @Test
    void virtualClusterTemplatesContainDrillDownAndCharts() {
        String overview = DashboardLayouts.VIRTUAL_CLUSTER_OVERVIEW;
        String detail = DashboardLayouts.VIRTUAL_CLUSTER_DETAIL;

        assertTrue(overview.contains("object-table"));
        assertTrue(overview.contains("virt-cluster-detail"));
        assertTrue(overview.contains("clusterError"));

        assertTrue(detail.contains("triangleWave"));
        assertTrue(detail.contains("\"type\": \"chart\""));
        assertFalse(detail.contains("object-table"));
    }
}
