package com.ispf.core.object;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectTreeTest {

    @Test
    void registersAndFindsNodes() {
        ObjectTree tree = new ObjectTree();
        PlatformObject device = new PlatformObject("d1", "root.pump1", ObjectType.DEVICE, "Pump 1", null, null);
        tree.register(device);

        assertThat(tree.findByPath("root.pump1")).isPresent();
        assertThat(tree.childrenOf("root")).hasSize(1);
    }

    @Test
    void rebuildChildIndexRestoresParentListing() {
        ObjectTree tree = new ObjectTree();
        PlatformObject folder = new PlatformObject(
                "folder",
                "root.platform.operator-apps",
                ObjectType.OPERATOR_APPS,
                "Operator Apps",
                null,
                null
        );
        PlatformObject app = new PlatformObject(
                "app",
                "root.platform.operator-apps.mini-tec",
                ObjectType.APPLICATION,
                "Mini-TEC",
                null,
                null
        );
        tree.register(folder);
        tree.register(app);
        tree.rebuildChildIndex();

        assertThat(tree.childrenOf("root.platform.operator-apps"))
                .extracting(PlatformObject::path)
                .containsExactly("root.platform.operator-apps.mini-tec");
    }

    @Test
    void ensureParentLinkIsIdempotent() {
        ObjectTree tree = new ObjectTree();
        PlatformObject folder = new PlatformObject(
                "folder",
                "root.platform.operator-apps",
                ObjectType.OPERATOR_APPS,
                "Operator Apps",
                null,
                null
        );
        PlatformObject app = new PlatformObject(
                "app",
                "root.platform.operator-apps.mini-tec",
                ObjectType.APPLICATION,
                "Mini-TEC",
                null,
                null
        );
        tree.register(folder);
        tree.register(app);
        tree.rebuildChildIndex();
        tree.ensureParentLink("root.platform.operator-apps.mini-tec");
        tree.ensureParentLink("root.platform.operator-apps.mini-tec");

        assertThat(tree.childrenOf("root.platform.operator-apps")).hasSize(1);
    }
}
