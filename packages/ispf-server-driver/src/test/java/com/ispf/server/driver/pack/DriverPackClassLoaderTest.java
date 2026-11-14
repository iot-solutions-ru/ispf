package com.ispf.server.driver.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DriverPackClassLoaderTest {

    @TempDir
    Path temp;

    @Test
    void bundledBouncyCastleWinsOverTheServerCopy() throws Exception {
        Path parentJar = jar(temp.resolve("parent.jar"), "parent-copy");
        Path packJar = jar(temp.resolve("pack.jar"), "pack-copy");
        try (URLClassLoader parent = new URLClassLoader(new URL[] {parentJar.toUri().toURL()}, null);
             DriverPackClassLoader pack = new DriverPackClassLoader(packJar.toUri().toURL(), parent)) {
            Class<?> marker = pack.loadClass("org.bouncycastle.packprobe.Marker");
            assertSame(pack, marker.getClassLoader());
            assertEquals("pack-copy", marker.getField("origin").get(null));
            assertFalse(parent.loadClass("org.bouncycastle.packprobe.Marker").getClassLoader() == pack);
        }
    }

    @Test
    void missingPackClassFallsBackToTheParent() throws Exception {
        Path parentJar = jar(temp.resolve("parent-fallback.jar"), "parent-copy");
        Path empty = temp.resolve("empty.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(empty))) {
            out.putNextEntry(new JarEntry("META-INF/placeholder.txt"));
            out.write("x".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try (URLClassLoader parent = new URLClassLoader(new URL[] {parentJar.toUri().toURL()}, null);
             DriverPackClassLoader pack = new DriverPackClassLoader(empty.toUri().toURL(), parent)) {
            Class<?> marker = pack.loadClass("org.bouncycastle.packprobe.Marker");
            assertSame(parent, marker.getClassLoader());
            assertEquals("parent-copy", marker.getField("origin").get(null));
        }
    }

    @Test
    void platformTypesStayOnTheParent() throws Exception {
        Path packJar = jar(temp.resolve("empty-pack.jar"), "unused");
        ClassLoader parent = DriverPackClassLoaderTest.class.getClassLoader();
        try (DriverPackClassLoader pack = new DriverPackClassLoader(packJar.toUri().toURL(), parent)) {
            Class<?> api = pack.loadClass("com.ispf.driver.DeviceDriver");
            assertSame(parent, api.getClassLoader());
        }
    }

    @Test
    void preferPackOnlyForCryptoAndMilo() {
        assertEquals(true, DriverPackClassLoader.preferPack("org.bouncycastle.asn1.iana.IANAObjectIdentifiers"));
        assertEquals(true, DriverPackClassLoader.preferPack("org.eclipse.milo.opcua.sdk.client.OpcUaClient"));
        assertEquals(false, DriverPackClassLoader.preferPack("com.ispf.driver.DeviceDriver"));
        assertEquals(false, DriverPackClassLoader.preferPack("io.netty.buffer.ByteBuf"));
    }

    private Path jar(Path destination, String origin) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests need a JDK compiler");
        Path sourceRoot = temp.resolve("src-" + origin);
        Path classRoot = temp.resolve("classes-" + origin);
        Path source = sourceRoot.resolve("org/bouncycastle/packprobe/Marker.java");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classRoot);
        Files.writeString(source, """
                package org.bouncycastle.packprobe;
                public class Marker {
                    public static final String origin = "%s";
                }
                """.formatted(origin));
        int compiled = compiler.run(null, null, null, "-d", classRoot.toString(), source.toString());
        assertEquals(0, compiled);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(destination))) {
            Path classFile = classRoot.resolve("org/bouncycastle/packprobe/Marker.class");
            jar.putNextEntry(new JarEntry("org/bouncycastle/packprobe/Marker.class"));
            jar.write(Files.readAllBytes(classFile));
            jar.closeEntry();
        }
        return destination;
    }
}
