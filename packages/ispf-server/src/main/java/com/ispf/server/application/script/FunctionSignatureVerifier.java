package com.ispf.server.application.script;

import com.ispf.core.model.DataRecord;

/**
 * Checks that a function signature was produced by the enrolled key of the named user.
 */
public interface FunctionSignatureVerifier {

    FunctionSignatureVerifier UNSIGNED = (policy, sourceBody, input) -> {
        throw new IllegalStateException("signaturePolicy " + policy + " requires a verifiable signature");
    };

    void verify(String policy, String sourceBody, DataRecord input);
}
