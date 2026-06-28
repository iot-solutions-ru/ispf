package com.ispf.server.function.java;

import com.ispf.core.function.ObjectJavaFunction;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavaFunctionCompiler {

    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+class\\s+(\\w+)");

    private JavaFunctionCompiler() {
    }

    record CompiledArtifact(String className, byte[] bytecode) {
    }

    static CompiledArtifact compile(String source) {
        JavaFunctionSecurity.validate(source);
        String normalized = normalizeSource(source);
        String className = resolveClassName(normalized);
        String fileName = className.contains(".")
                ? className.replace('.', '/') + ".java"
                : className + ".java";

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK Java compiler is not available on this runtime");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standardManager = compiler.getStandardFileManager(diagnostics, null, null);
        Map<String, ByteArrayOutputStream> classBytes = new LinkedHashMap<>();
        try (InMemoryFileManager fileManager = new InMemoryFileManager(standardManager, classBytes)) {
            JavaFileObject sourceFile = new StringJavaFileObject(fileName, normalized);
            Boolean ok = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-classpath", JavaFunctionCompileClasspath.get()),
                    null,
                    List.of(sourceFile)
            ).call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IllegalArgumentException(formatDiagnostics(diagnostics));
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Java function compilation failed: " + ex.getMessage(), ex);
        }

        ByteArrayOutputStream bytecodeStream = classBytes.get(className);
        if (bytecodeStream == null) {
            bytecodeStream = classBytes.get(className.substring(className.lastIndexOf('.') + 1));
        }
        if (bytecodeStream == null) {
            throw new IllegalArgumentException("Compiled class not found for " + className);
        }
        byte[] bytecode = bytecodeStream.toByteArray();
        return new CompiledArtifact(className, bytecode);
    }

    static String normalizeSource(String source) {
        String trimmed = source.trim();
        if (trimmed.contains("class ")) {
            return trimmed;
        }
        throw new IllegalArgumentException(
                "Java function must declare a public class implementing ObjectJavaFunction"
        );
    }

    static String resolveClassName(String source) {
        Matcher matcher = PUBLIC_CLASS.matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Java function must contain a public class declaration");
        }
        return matcher.group(1);
    }

    static ObjectJavaFunction instantiate(CompiledArtifact artifact) {
        try {
            InMemoryClassLoader loader = new InMemoryClassLoader(
                    Map.of(artifact.className(), artifact.bytecode()),
                    ObjectJavaFunction.class.getClassLoader()
            );
            Class<?> clazz = loader.loadClass(artifact.className());
            if (!ObjectJavaFunction.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(
                        "Java function class must implement ObjectJavaFunction: " + artifact.className()
                );
            }
            return (ObjectJavaFunction) clazz.getDeclaredConstructor().newInstance();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Java function class: " + ex.getMessage(), ex);
        }
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder builder = new StringBuilder("Java compilation failed");
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                builder.append("\n")
                        .append(diagnostic.getLineNumber())
                        .append(":")
                        .append(diagnostic.getColumnNumber())
                        .append(" ")
                        .append(diagnostic.getMessage(null));
            }
        }
        return builder.toString();
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classBytes;

        InMemoryFileManager(StandardJavaFileManager fileManager, Map<String, ByteArrayOutputStream> classBytes) {
            super(fileManager);
            this.classBytes = classBytes;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling
        ) {
            return new SimpleJavaFileObject(URI.create("mem:///" + className + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    classBytes.put(className, out);
                    return out;
                }
            };
        }
    }

    private static final class InMemoryClassLoader extends ClassLoader {
        private final Map<String, byte[]> classes;

        InMemoryClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
            super(parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                return super.findClass(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
