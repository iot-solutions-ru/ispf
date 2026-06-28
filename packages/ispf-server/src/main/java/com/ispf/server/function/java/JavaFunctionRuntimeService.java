package com.ispf.server.function.java;

import com.ispf.core.function.JavaFunctionContext;
import com.ispf.core.function.ObjectJavaFunction;
import com.ispf.core.model.DataRecord;
import com.ispf.core.object.FunctionDescriptor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JavaFunctionRuntimeService {

    private final Map<String, CompiledJavaFunction> compiled = new ConcurrentHashMap<>();

    public void syncOnSave(String objectPath, FunctionDescriptor function, FunctionDescriptor before) {
        if (before != null && before.hasJavaBody()) {
            String beforeKey = key(objectPath, before.name());
            if (!function.hasJavaBody() || !before.name().equals(function.name())) {
                compiled.remove(beforeKey);
            }
        }
        if (function.hasJavaBody()) {
            compileAndRegister(objectPath, function);
        } else if (before != null && before.hasJavaBody() && before.name().equals(function.name())) {
            compiled.remove(key(objectPath, function.name()));
        }
    }

    public void unregister(String objectPath, String functionName) {
        compiled.remove(key(objectPath, functionName));
    }

    public CompiledJavaFunction get(String objectPath, String functionName) {
        return compiled.get(key(objectPath, functionName));
    }

    public void compileAndRegister(String objectPath, FunctionDescriptor function) {
        JavaFunctionCompiler.CompiledArtifact artifact = JavaFunctionCompiler.compile(function.sourceBody());
        ObjectJavaFunction instance = JavaFunctionCompiler.instantiate(artifact);
        compiled.put(
                key(objectPath, function.name()),
                new CompiledJavaFunction(function.name(), artifact.className(), instance)
        );
    }

    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        CompiledJavaFunction fn = get(objectPath, functionName);
        if (fn == null) {
            throw new IllegalStateException("Java function is not compiled: " + functionName);
        }
        return fn.instance().invoke(input, new JavaFunctionContext(objectPath, functionName));
    }

    private static String key(String objectPath, String functionName) {
        return objectPath + "#" + functionName;
    }

    public record CompiledJavaFunction(String functionName, String className, ObjectJavaFunction instance) {
    }
}
