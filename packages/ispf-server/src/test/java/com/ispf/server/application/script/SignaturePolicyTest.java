package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignaturePolicyTest {

    @Test
    void requiredPolicyRejectsAMissingSignature() {
        String body = "{\"signaturePolicy\":\"REQUIRED\",\"steps\":[]}";
        assertThrows(IllegalStateException.class, () -> SignaturePolicy.require(body, null));
        DataRecord signed = DataRecord.single(
                DataSchema.builder("in").field("signature", FieldType.STRING).build(),
                Map.of("signature", "abc")
        );
        assertDoesNotThrow(() -> SignaturePolicy.require(body, signed));
    }
}
