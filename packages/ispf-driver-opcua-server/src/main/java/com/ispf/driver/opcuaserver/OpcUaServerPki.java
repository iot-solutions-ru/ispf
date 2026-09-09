package com.ispf.driver.opcuaserver;

import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Period;
import java.util.Enumeration;

public final class OpcUaServerPki implements AutoCloseable {

    static final String P12_NAME = "identity.p12";
    static final String CER_NAME = "application.cer";
    private static final char[] PASSWORD = new char[0];

    private final KeyPair keyPair;
    private final X509Certificate certificate;
    private final DefaultTrustListManager trustList;

    private OpcUaServerPki(KeyPair keyPair, X509Certificate certificate, DefaultTrustListManager trustList) {
        this.keyPair = keyPair;
        this.certificate = certificate;
        this.trustList = trustList;
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public DefaultTrustListManager trustList() {
        return trustList;
    }

    public static OpcUaServerPki loadOrCreate(Path pkiDir, String commonName, String applicationUri) throws Exception {
        Files.createDirectories(pkiDir);
        Path p12 = pkiDir.resolve(P12_NAME);
        KeyPair keyPair;
        X509Certificate certificate;
        if (Files.isRegularFile(p12)) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(p12)) {
                store.load(in, PASSWORD);
            }
            Enumeration<String> aliases = store.aliases();
            if (!aliases.hasMoreElements()) {
                throw new IllegalStateException("Empty OPC UA server keystore: " + p12);
            }
            String alias = aliases.nextElement();
            PrivateKey privateKey = (PrivateKey) store.getKey(alias, PASSWORD);
            certificate = (X509Certificate) store.getCertificate(alias);
            keyPair = new KeyPair(certificate.getPublicKey(), privateKey);
        } else {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            certificate = new SelfSignedCertificateBuilder(keyPair)
                    .setCommonName(commonName)
                    .setOrganization("ISPF")
                    .setApplicationUri(applicationUri)
                    .addDnsName("localhost")
                    .addIpAddress("127.0.0.1")
                    .setValidityPeriod(Period.ofYears(3))
                    .build();
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, PASSWORD);
            store.setKeyEntry("opcua", keyPair.getPrivate(), PASSWORD, new Certificate[]{certificate});
            try (OutputStream out = Files.newOutputStream(p12)) {
                store.store(out, PASSWORD);
            }
            Files.write(pkiDir.resolve(CER_NAME), certificate.getEncoded());
        }
        return new OpcUaServerPki(keyPair, certificate, new DefaultTrustListManager(pkiDir.toFile()));
    }

    @Override
    public void close() {
        try {
            trustList.close();
        } catch (Exception ignored) {
            // best effort
        }
    }
}
