package com.ispf.server.query.oq;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.ref.PlatformRefExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectQueryJoinResolverTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private PlatformRefExecutor platformRefExecutor;

    @Mock
    private com.ispf.core.object.ObjectTree tree;

    private ObjectQueryJoinResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ObjectQueryJoinResolver(objectManager, platformRefExecutor);
        when(objectManager.tree()).thenReturn(tree);
    }

    @Test
    void parentJoinResolvesParentPath() {
        PlatformObject child = stubNode("root.platform.devices.site-a.tank-1", ObjectType.DEVICE);
        PlatformObject parent = stubNode("root.platform.devices.site-a", ObjectType.DEVICE);
        when(tree.findByPath("root.platform.devices.site-a")).thenReturn(Optional.of(parent));

        ObjectQueryJoinSpec join = new ObjectQueryJoinSpec(
                "parent",
                "left",
                null,
                null,
                null,
                new ObjectQueryJoinOnSpec(JoinKind.PARENT, null, null, null, null)
        );
        Optional<String> joined = resolver.resolveJoin(
                join,
                Map.of("row", child.path()),
                "row",
                "root.platform.queries.test"
        );
        assertThat(joined).contains(parent.path());
    }

    @Test
    void pathSubstringJoinFindsCatalogEntry() {
        PlatformObject device = stubNode("root.platform.devices.lab-userA-01", ObjectType.DEVICE);
        PlatformObject catalog = stubNode("root.platform.instances.lab-userA-01", ObjectType.CUSTOM);
        when(tree.all()).thenReturn(java.util.List.of(catalog));

        ObjectQueryJoinSpec join = new ObjectQueryJoinSpec(
                "instance",
                "left",
                "root.platform.instances.*",
                null,
                null,
                new ObjectQueryJoinOnSpec(JoinKind.PATH_SUBSTRING, null, null, "lab-userA-01", null)
        );
        Optional<String> joined = resolver.resolveJoin(
                join,
                Map.of("row", device.path()),
                "row",
                "root.platform.queries.test"
        );
        assertThat(joined).contains(catalog.path());
    }

    @Test
    void lookupJoinMatchesLeafName() {
        PlatformObject device = stubNode("root.platform.devices.pump-01", ObjectType.DEVICE);
        PlatformObject lookup = stubNode("root.platform.instances.pump-01", ObjectType.CUSTOM);
        when(tree.all()).thenReturn(java.util.List.of(lookup));
        when(platformRefExecutor.read(any(), any())).thenReturn(Optional.of("pump-01"));

        ObjectQueryJoinSpec join = new ObjectQueryJoinSpec(
                "instance",
                "left",
                "root.platform.instances.*",
                null,
                null,
                new ObjectQueryJoinOnSpec(JoinKind.LOOKUP, "{row}/displayName", null, null, "root.platform.instances.*")
        );
        Optional<String> joined = resolver.resolveJoin(
                join,
                Map.of("row", device.path()),
                "row",
                "root.platform.queries.test"
        );
        assertThat(joined).contains(lookup.path());
    }

    private static PlatformObject stubNode(String path, ObjectType type) {
        return new PlatformObject(path, path, type, leaf(path), "", null);
    }

    private static String leaf(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
