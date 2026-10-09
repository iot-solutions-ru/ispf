package com.ispf.server.binding;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SqlBindingCatalogRevisionTest {

    @Autowired
    private SqlBindingObjectService sqlBindingObjectService;
    @Autowired
    private BindingRefreshAfterCommit bindingRefreshAfterCommit;
    @Autowired
    private ObjectManager objectManager;

    @Test
    void refreshingBindingsLeavesTheCatalogFolderRevisionUnchanged() {
        long revision = catalogRevision();

        sqlBindingObjectService.refreshScheduledBindings();
        bindingRefreshAfterCommit.scheduleRefreshAfterFunction("root.platform", "catalogRevisionProbe");

        assertThat(catalogRevision()).isEqualTo(revision);
    }

    private long catalogRevision() {
        return objectManager.tree().require(SqlBindingObjectService.BINDINGS_ROOT).revision();
    }
}
