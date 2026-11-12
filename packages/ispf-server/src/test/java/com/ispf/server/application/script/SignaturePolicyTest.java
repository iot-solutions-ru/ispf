package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignaturePolicyTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiredPolicyRejectsAMissingSignature() {
        String body = "{\"signaturePolicy\":\"REQUIRED\",\"steps\":[]}";
        assertThrows(IllegalStateException.class, () -> SignaturePolicy.require(body, null));
    }

    @Test
    void requiredPolicyRejectsSignatureWithoutPrincipal() {
        String body = "{\"signaturePolicy\":\"REQUIRED\",\"steps\":[]}";
        DataRecord signed = DataRecord.single(
                DataSchema.builder("in").field("signature", FieldType.STRING).build(),
                Map.of("signature", "abc")
        );
        assertThrows(IllegalStateException.class, () -> SignaturePolicy.require(body, signed));
    }

    @Test
    void requiredPolicyAcceptsPrincipalAndSignatureMaterial() {
        String body = "{\"signaturePolicy\":\"REQUIRED\",\"steps\":[]}";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "operator",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                )
        );
        DataRecord signed = DataRecord.single(
                DataSchema.builder("in").field("signature", FieldType.STRING).build(),
                Map.of("signature", "abc")
        );
        assertDoesNotThrow(() -> SignaturePolicy.require(body, signed));
    }
}
