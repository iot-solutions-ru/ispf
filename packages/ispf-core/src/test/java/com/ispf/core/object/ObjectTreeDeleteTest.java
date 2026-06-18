package com.ispf.core.object;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectTreeDeleteTest {

    @Test
    void deletesObjectAndDescendants() {
        ObjectTree tree = new ObjectTree();
        tree.register(new PlatformObject("a", "root.a", ObjectType.CUSTOM, "A", null, null));
        tree.register(new PlatformObject("b", "root.a.b", ObjectType.CUSTOM, "B", null, null));

        tree.delete("root.a");

        assertThat(tree.findByPath("root.a")).isEmpty();
        assertThat(tree.findByPath("root.a.b")).isEmpty();
        assertThat(tree.findByPath("root")).isPresent();
    }

    @Test
    void cannotDeleteRoot() {
        ObjectTree tree = new ObjectTree();
        assertThatThrownBy(() -> tree.delete("root")).isInstanceOf(IllegalArgumentException.class);
    }
}
