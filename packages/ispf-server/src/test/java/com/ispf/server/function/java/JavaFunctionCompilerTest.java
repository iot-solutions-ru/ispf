package com.ispf.server.function.java;

import com.ispf.core.function.ObjectJavaFunction;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFunctionCompilerTest {

  private static final String ECHO_SOURCE = """
      import com.ispf.core.function.ObjectJavaFunction;
      import com.ispf.core.function.JavaFunctionContext;
      import com.ispf.core.model.DataRecord;
      import com.ispf.core.model.DataSchema;
      import com.ispf.core.model.FieldType;
      import java.util.Map;

      public class EchoJavaFn implements ObjectJavaFunction {
          @Override
          public DataRecord invoke(DataRecord input, JavaFunctionContext context) {
              Object value = input != null && input.rowCount() > 0 ? input.firstRow().get("value") : null;
              DataSchema schema = DataSchema.builder("out").field("value", FieldType.STRING).build();
              return DataRecord.single(schema, Map.of("value", value == null ? "" : String.valueOf(value)));
          }
      }
      """;

    @Test
    void compilesAndInstantiatesEchoFunction() {
        JavaFunctionCompiler.CompiledArtifact artifact = JavaFunctionCompiler.compile(ECHO_SOURCE);
        assertEquals("EchoJavaFn", artifact.className());
        ObjectJavaFunction fn = JavaFunctionCompiler.instantiate(artifact);
        assertNotNull(fn.invoke(null, new com.ispf.core.function.JavaFunctionContext("root.test", "echo")));
    }

    @Test
    void compileClasspathIncludesIspfCoreFromBootJarLayout() throws Exception {
        JavaFunctionCompileClasspath.clearCacheForTests();
        Path bootJar = Path.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)[0]);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                bootJar.toString().endsWith(".jar") && Files.exists(bootJar),
                "boot jar layout test requires jar classpath"
        );
        try (JarFile jar = new JarFile(bootJar.toFile())) {
            boolean hasBootInf = jar.stream().anyMatch(e -> e.getName().startsWith("BOOT-INF/lib/ispf-core"));
            org.junit.jupiter.api.Assumptions.assumeTrue(hasBootInf, "not a Spring Boot fat jar");
        }
        String classpath = JavaFunctionCompileClasspath.get();
        assertNotNull(classpath);
        org.junit.jupiter.api.Assertions.assertTrue(
                classpath.toLowerCase().contains("ispf-core"),
                () -> "expected ispf-core on compile classpath but got: " + classpath
        );
    }

    @Test
    void manifestClasspathJarResolvesIspfCore() throws Exception {
        Path dir = Files.createTempDirectory("ispf-cp-");
        Path jarPath = dir.resolve("gradle-javaexec-classpath.jar");
        Path core = dir.resolve("ispf-core-0.9.208.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(
                "Class-Path",
                core.toUri() + " libs/ispf-core-relative.jar"
        );
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            // classpath pointer jar, same shape as Gradle bootRun
        }
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JavaFunctionCompileClasspath.addManifestClasspath(jarPath, jar, entries);
        }
        assertTrue(entries.stream().anyMatch(entry -> entry.contains("ispf-core-0.9.208.jar")));
        assertTrue(entries.stream().anyMatch(entry -> entry.replace('\\', '/').endsWith("libs/ispf-core-relative.jar")));
    }

    @Test
    void manifestClasspathIsReadOnlyForASingleJar() {
        String sep = java.io.File.pathSeparator;
        assertTrue(JavaFunctionCompileClasspath.soleClasspathJar("C:/gradle/classpath.jar"));
        assertFalse(JavaFunctionCompileClasspath.soleClasspathJar("a.jar" + sep + "b.jar"));
        assertFalse(JavaFunctionCompileClasspath.soleClasspathJar("build/classes/java/main"));
    }

    @Test
    void malformedManifestFileUrlIsNotDropped() {
        Path jarPath = Path.of("gradle-javaexec-classpath.jar");
        assertThrows(IllegalArgumentException.class,
                () -> JavaFunctionCompileClasspath.resolveManifestEntry(jarPath, "file://not a uri"));
    }

    @Test
    void rejectsForbiddenConstructs() {
        String source = """
            public class BadFn implements com.ispf.core.function.ObjectJavaFunction {
                public com.ispf.core.model.DataRecord invoke(
                        com.ispf.core.model.DataRecord input,
                        com.ispf.core.function.JavaFunctionContext context) {
                    Runtime.getRuntime();
                    return input;
                }
            }
            """;
        assertThrows(IllegalArgumentException.class, () -> JavaFunctionCompiler.compile(source));
    }

    @Test
    void rejectsProcessHandleAndUrlClassLoader() {
        assertThrows(IllegalArgumentException.class, () -> JavaFunctionCompiler.compile("""
            public class BadFn implements com.ispf.core.function.ObjectJavaFunction {
                public com.ispf.core.model.DataRecord invoke(
                        com.ispf.core.model.DataRecord input,
                        com.ispf.core.function.JavaFunctionContext context) {
                    ProcessHandle.current();
                    return input;
                }
            }
            """));
        assertThrows(IllegalArgumentException.class, () -> JavaFunctionCompiler.compile("""
            public class BadFn implements com.ispf.core.function.ObjectJavaFunction {
                public com.ispf.core.model.DataRecord invoke(
                        com.ispf.core.model.DataRecord input,
                        com.ispf.core.function.JavaFunctionContext context) {
                    new URLClassLoader(new java.net.URL[0]);
                    return input;
                }
            }
            """));
    }
}
