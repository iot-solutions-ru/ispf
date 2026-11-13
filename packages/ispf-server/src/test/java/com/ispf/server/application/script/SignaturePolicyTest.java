package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.security.SignatureKeyDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignaturePolicyTest {

    private static final String REQUIRED = "{\"signaturePolicy\":\"REQUIRED\",\"steps\":[]}";
    private static final String DUAL = "{\"signaturePolicy\":\"DUAL\",\"steps\":[]}";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiredPolicyRejectsAMissingSignature() {
        assertThrows(IllegalStateException.class, () -> SignaturePolicy.require(REQUIRED, null));
    }

    @Test
    void requiredPolicyRejectsSignatureWithoutPrincipal() {
        DataRecord signed = signed("signature", "abc");
        assertThrows(IllegalStateException.class, () -> SignaturePolicy.require(REQUIRED, signed));
    }

    @Test
    void requiredPolicyRejectsASignatureThatDoesNotVerify() throws Exception {
        login("operator");
        KeyPair pair = keyPair();
        Ed25519FunctionSignatureVerifier verifier = verifier(Map.of("operator", publicKey(pair)));
        DataRecord forged = signed("signature", "abc");
        assertThrows(IllegalStateException.class, () -> SignaturePolicy.require(REQUIRED, forged, verifier));
    }

    @Test
    void requiredPolicyAcceptsTheEnrolledUsersSignature() throws Exception {
        login("operator");
        KeyPair pair = keyPair();
        Ed25519FunctionSignatureVerifier verifier = verifier(Map.of("operator", publicKey(pair)));
        DataRecord signed = signed("signature", sign(pair, "REQUIRED", REQUIRED, "operator", ""));
        assertDoesNotThrow(() -> SignaturePolicy.require(REQUIRED, signed, verifier));
    }

    @Test
    void dualPolicyRequiresADifferentSecondSigner() throws Exception {
        login("operator");
        KeyPair pair = keyPair();
        Ed25519FunctionSignatureVerifier verifier = verifier(Map.of("operator", publicKey(pair)));
        String signature = sign(pair, "DUAL", DUAL, "operator", "operator");
        DataRecord samePerson = DataRecord.single(
                schema(),
                Map.of("signature", signature, "countersigner", "operator", "countersignature", signature)
        );
        assertThrows(IllegalStateException.class, () -> SignaturePolicy.require(DUAL, samePerson, verifier));
    }

    @Test
    void dualPolicyAcceptsTwoEnrolledSigners() throws Exception {
        login("operator");
        KeyPair operator = keyPair();
        KeyPair supervisor = keyPair();
        Ed25519FunctionSignatureVerifier verifier = verifier(Map.of(
                "operator", publicKey(operator),
                "supervisor", publicKey(supervisor)
        ));
        DataRecord signed = DataRecord.single(schema(), Map.of(
                "signature", sign(operator, "DUAL", DUAL, "operator", "supervisor"),
                "countersigner", "supervisor",
                "countersignature", sign(supervisor, "DUAL", DUAL, "operator", "supervisor")
        ));
        assertDoesNotThrow(() -> SignaturePolicy.require(DUAL, signed, verifier));
    }

    private static Ed25519FunctionSignatureVerifier verifier(Map<String, String> enrolled) {
        Map<String, String> keys = new HashMap<>(enrolled);
        SignatureKeyDirectory directory = new SignatureKeyDirectory() {
            @Override
            public void upsert(String username, String publicKey) {
                keys.put(username, publicKey);
            }

            @Override
            public Optional<String> find(String username) {
                return Optional.ofNullable(keys.get(username));
            }
        };
        return new Ed25519FunctionSignatureVerifier(directory);
    }

    private static void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                )
        );
    }

    private static DataSchema schema() {
        return DataSchema.builder("in")
                .field("signature", FieldType.STRING)
                .field("countersigner", FieldType.STRING)
                .field("countersignature", FieldType.STRING)
                .build();
    }

    private static DataRecord signed(String field, String value) {
        return DataRecord.single(
                DataSchema.builder("in").field(field, FieldType.STRING).build(),
                Map.of(field, value)
        );
    }

    private static KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String publicKey(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    private static String sign(KeyPair pair, String policy, String body, String signer, String countersigner) throws Exception {
        byte[] message = FunctionSignature.message(policy, body, signer, countersigner, null);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(pair.getPrivate());
        signature.update(message);
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
