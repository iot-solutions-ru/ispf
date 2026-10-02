package com.ispf.server.function;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.datasource.DataSourceFunctionSupport;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecuteQueryPathResolverTest {

    private static final String DATA_SOURCE = "root.platform.data-sources.demo";
    private static final String HUB = "root.platform.singletons.app";

    @Mock
    private ObjectManager objectManager;
    @Mock
    private com.ispf.core.object.ObjectTree objectTree;

    @BeforeEach
    void setUp() {
        when(objectManager.tree()).thenReturn(objectTree);
    }

    @Test
    void resolvesHostWhenFunctionLivesOnDataSource() {
        PlatformObject ds = new PlatformObject("1", DATA_SOURCE, ObjectType.DATA_SOURCE, "Demo", "", "");
        FunctionDescriptor descriptor = DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION;
        when(objectTree.findByPath(DATA_SOURCE)).thenReturn(Optional.of(ds));

        assertThat(ExecuteQueryPathResolver.tryResolve(objectManager, DATA_SOURCE, descriptor))
                .contains(DATA_SOURCE);
    }

    @Test
    void resolvesDescriptorPathWhenFunctionOnHub() {
        PlatformObject ds = new PlatformObject("1", DATA_SOURCE, ObjectType.DATA_SOURCE, "Demo", "", "");
        FunctionDescriptor descriptor = new FunctionDescriptor(
                "executeQuery",
                "",
                DataSourceFunctionSupport.EXECUTE_QUERY_INPUT_SCHEMA,
                DataSourceFunctionSupport.EXECUTE_QUERY_OUTPUT_SCHEMA,
                null,
                null,
                DATA_SOURCE,
                null,
                java.util.List.of()
        );
        when(objectTree.findByPath(DATA_SOURCE)).thenReturn(Optional.of(ds));

        assertThat(ExecuteQueryPathResolver.tryResolve(objectManager, HUB, descriptor))
                .contains(DATA_SOURCE);
    }

    @Test
    void emptyWhenHubWithoutDataSourcePath() {
        PlatformObject hub = new PlatformObject("2", HUB, ObjectType.APPLICATION, "App", "", "");
        FunctionDescriptor descriptor = DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION;
        when(objectTree.findByPath(HUB)).thenReturn(Optional.of(hub));

        assertThat(ExecuteQueryPathResolver.tryResolve(objectManager, HUB, descriptor)).isEmpty();
    }

    @Test
    void requireResolveThrowsWhenMissingPath() {
        PlatformObject hub = new PlatformObject("2", HUB, ObjectType.APPLICATION, "App", "", "");
        when(objectTree.findByPath(HUB)).thenReturn(Optional.of(hub));

        assertThatThrownBy(() -> ExecuteQueryPathResolver.requireResolve(
                objectManager,
                HUB,
                DataSourceFunctionSupport.EXECUTE_QUERY_FUNCTION
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataSourcePath is required");
    }
}
