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

    @Test
    void rejectsDuplicateObjectId() {
        ObjectTree tree = new ObjectTree();
        tree.register(new PlatformObject("same-id", "root.a", ObjectType.DEVICE, "A", null, null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        tree.register(new PlatformObject("same-id", "root.b", ObjectType.DEVICE, "B", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Object id already registered");

        assertThat(tree.findByPath("root.b")).isEmpty();
        assertThat(tree.findById("same-id")).isPresent();
        assertThat(tree.findById("same-id").orElseThrow().path()).isEqualTo("root.a");
    }

    @Test
    void resolveChildPathRejectsDottedName() {
        ObjectTree tree = new ObjectTree();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tree.resolveChildPath("root", "a.b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain");
        assertThat(tree.resolveChildPath("root", "pump")).isEqualTo("root.pump");
    }

    @Test
    void prefixAndTypeIndexesNarrowCandidatesAndTrackMutations() {
        ObjectTree tree = new ObjectTree();
        tree.register(new PlatformObject("d1", "root.platform.devices.pump-1", ObjectType.DEVICE, "Pump 1", null, null));
        tree.register(new PlatformObject("d2", "root.platform.devices.pump-2", ObjectType.DEVICE, "Pump 2", null, null));
        tree.register(new PlatformObject("h1", "root.platform.devices.hub-1", ObjectType.CUSTOM, "Hub 1", null, null));
        tree.register(new PlatformObject("r1", "root.platform.reports.daily", ObjectType.REPORT, "Daily", null, null));

        assertThat(tree.findByPathPrefix("root.platform.devices.pump-"))
                .extracting(PlatformObject::path)
                .containsExactly("root.platform.devices.pump-1", "root.platform.devices.pump-2");

        assertThat(tree.findCandidates("root.platform.devices.", java.util.Set.of(ObjectType.DEVICE)))
                .extracting(PlatformObject::path)
                .containsExactly("root.platform.devices.pump-1", "root.platform.devices.pump-2");

        tree.updateNodeType("root.platform.devices.hub-1", ObjectType.DEVICE);
        assertThat(tree.findByTypes(java.util.Set.of(ObjectType.DEVICE)))
                .extracting(PlatformObject::path)
                .containsExactlyInAnyOrder(
                        "root.platform.devices.pump-1",
                        "root.platform.devices.pump-2",
                        "root.platform.devices.hub-1"
                );
        assertThat(tree.findByTypes(java.util.Set.of(ObjectType.CUSTOM))).isEmpty();

        tree.delete("root.platform.devices.pump-1");
        assertThat(tree.findByTypes(java.util.Set.of(ObjectType.DEVICE)))
                .extracting(PlatformObject::path)
                .containsExactlyInAnyOrder("root.platform.devices.pump-2", "root.platform.devices.hub-1");

        tree.register(new PlatformObject("c1", "root.platform.devices.hub-1.child", ObjectType.DEVICE, "Child", null, null));
        tree.delete("root.platform.devices.hub-1");
        assertThat(tree.findByTypes(java.util.Set.of(ObjectType.DEVICE)))
                .extracting(PlatformObject::path)
                .containsExactly("root.platform.devices.pump-2");

        tree.rebuildChildIndex();
        assertThat(tree.findByTypes(java.util.Set.of(ObjectType.DEVICE, ObjectType.REPORT)))
                .extracting(PlatformObject::path)
                .containsExactlyInAnyOrder("root.platform.devices.pump-2", "root.platform.reports.daily");
    }
}
