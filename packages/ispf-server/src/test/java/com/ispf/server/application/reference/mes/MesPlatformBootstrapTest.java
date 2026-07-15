package com.ispf.server.application.reference.mes;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.PlatformBootstrap;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MesPlatformBootstrapTest {

    @Autowired
    private MesPlatformBootstrap mesPlatformBootstrap;

    @Autowired
    private ObjectManager objectManager;

    @Test
    void platformBootstrap_doesNotSeedMesRoot() {
        PlatformBootstrap bootstrap = new PlatformBootstrap();
        ObjectTree tree = new ObjectTree();
        bootstrap.initialize(tree);
        assertThat(tree.findByPath(MesPaths.MES_ROOT)).isEmpty();
    }

    @Test
    void ensureCatalog_createsMesFolders() {
        mesPlatformBootstrap.ensureCatalog();
        assertThat(objectManager.tree().findByPath(MesPaths.MES_ROOT)).isPresent();
        assertThat(objectManager.tree().findByPath(MesPaths.MES_ROOT).orElseThrow().type()).isEqualTo(ObjectType.MES);
        assertThat(objectManager.tree().findByPath(MesPaths.WORK_ORDERS)).isPresent();
        assertThat(objectManager.tree().findByPath(MesPaths.LOTS)).isPresent();
        assertThat(objectManager.tree().findByPath(MesPaths.INSTANCES)).isPresent();
    }

    @Test
    void mesPaths_coverCatalogFolders() {
        assertThat(MesPaths.WORK_ORDERS).isEqualTo("root.platform.mes.work-orders");
        assertThat(MesPaths.INSTANCES).isEqualTo("root.platform.mes.instances");
    }
}
