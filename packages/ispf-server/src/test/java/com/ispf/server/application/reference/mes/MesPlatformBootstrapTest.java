package com.ispf.server.application.reference.mes;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.PlatformBootstrap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MesPlatformBootstrapTest {

    @Test
    void platformBootstrap_seedsMesRoot() {
        PlatformBootstrap bootstrap = new PlatformBootstrap();
        ObjectTree tree = new ObjectTree();
        bootstrap.initialize(tree);
        assertThat(tree.findByPath(MesPaths.MES_ROOT)).isPresent();
        assertThat(tree.findByPath(MesPaths.MES_ROOT).orElseThrow().type()).isEqualTo(ObjectType.MES);
    }

    @Test
    void mesPaths_coverCatalogFolders() {
        assertThat(MesPaths.WORK_ORDERS).isEqualTo("root.platform.mes.work-orders");
        assertThat(MesPaths.INSTANCES).isEqualTo("root.platform.mes.instances");
    }
}
