package com.ispf.server.bootstrap;

import com.ispf.core.object.ObjectTree;
import com.ispf.server.config.BootstrapProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformBootstrapFixturesTest {

    @Test
    void initialize_withoutFixtures_seedsCatalogOnly() {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setFixturesEnabled(false);
        PlatformBootstrap bootstrap = new PlatformBootstrap(properties);
        ObjectTree tree = new ObjectTree();

        bootstrap.initialize(tree);

        assertThat(tree.findByPath("root.platform")).isPresent();
        assertThat(tree.findByPath("root.platform.devices")).isPresent();
        assertThat(tree.findByPath("root.platform.devices.demo-sensor-01")).isEmpty();
        assertThat(tree.findByPath("root.platform.dashboards.demo-sensor")).isEmpty();
        assertThat(tree.findByPath("root.platform.workflows.demo-alarm-handler")).isEmpty();
    }

    @Test
    void initialize_withFixtures_seedsDemoObjects() {
        BootstrapProperties properties = new BootstrapProperties();
        properties.setFixturesEnabled(true);
        PlatformBootstrap bootstrap = new PlatformBootstrap(properties);
        ObjectTree tree = new ObjectTree();

        bootstrap.initialize(tree);

        assertThat(tree.findByPath("root.platform.devices.demo-sensor-01")).isPresent();
        assertThat(tree.findByPath("root.platform.dashboards.demo-sensor")).isPresent();
        assertThat(tree.findByPath("root.platform.workflows.demo-alarm-handler")).isPresent();
    }
}
