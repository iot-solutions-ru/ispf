package com.ispf.server.bootstrap;

import com.ispf.core.object.ObjectTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformBootstrapFixturesTest {

    @Test
    void initialize_seedsCatalogOnly() {
        PlatformBootstrap bootstrap = new PlatformBootstrap();
        ObjectTree tree = new ObjectTree();

        bootstrap.initialize(tree);

        assertThat(tree.findByPath("root.platform")).isPresent();
        assertThat(tree.findByPath("root.platform.devices")).isPresent();
        assertThat(tree.findByPath("root.platform.dashboards")).isPresent();
        assertThat(tree.findByPath("root.platform.workflows")).isPresent();
        assertThat(tree.findByPath("root.platform.devices.demo-sensor-01")).isEmpty();
        assertThat(tree.findByPath("root.platform.dashboards.demo-sensor")).isEmpty();
        assertThat(tree.findByPath("root.platform.workflows.demo-alarm-handler")).isEmpty();
    }
}
