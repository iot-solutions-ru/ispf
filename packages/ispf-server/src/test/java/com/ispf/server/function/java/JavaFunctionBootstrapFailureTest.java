package com.ispf.server.function.java;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JavaFunctionBootstrapFailureTest {

    private static final String PATH = "root.platform.devices.pump";

    @Mock
    private ObjectManager objectManager;
    @Mock
    private JavaFunctionRuntimeService runtimeService;
    @Mock
    private Environment environment;
    @Mock
    private ObjectTree tree;

    @Test
    void compileFailureMarksTheFunctionNotReady() {
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                PATH,
                ObjectType.DEVICE,
                "pump",
                "",
                null
        );
        FunctionDescriptor function = new FunctionDescriptor(
                "tick",
                "",
                null,
                null,
                "java",
                "not java",
                null,
                "1"
        );
        node.addFunction(function);
        when(runtimeService.isEnabled()).thenReturn(true);
        when(objectManager.tree()).thenReturn(tree);
        when(tree.all()).thenReturn(List.of(node));
        doThrow(new IllegalArgumentException("bad class"))
                .when(runtimeService)
                .compileAndRegister(PATH, function);

        new JavaFunctionBootstrap(objectManager, runtimeService, environment).warmUpCompiledFunctions();

        org.mockito.Mockito.verify(runtimeService).markBootstrapFailure(
                org.mockito.ArgumentMatchers.eq(PATH),
                org.mockito.ArgumentMatchers.eq("tick"),
                any(RuntimeException.class)
        );
    }

    @Test
    void markedFailureIsReportedOnInvoke() {
        com.ispf.server.config.FunctionProperties properties = new com.ispf.server.config.FunctionProperties();
        JavaFunctionRuntimeService runtime = new JavaFunctionRuntimeService(properties);
        runtime.markBootstrapFailure(PATH, "tick", new IllegalArgumentException("bad class"));

        assertFalse(runtime.isReady(PATH, "tick"));
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runtime.invoke(PATH, "tick", null)
        );
        assertTrue(error.getMessage().contains("Java function is not ready: tick"));
        assertTrue(error.getMessage().contains("bad class"));
    }
}
