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
}
