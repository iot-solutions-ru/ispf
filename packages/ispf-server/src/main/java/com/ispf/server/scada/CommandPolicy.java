package com.ispf.server.scada;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Operator command acknowledgment policies for SCADA faceplates.
 */
public enum CommandPolicy {

    confirm,
    reauth,
    four_eyes;

    /**
     * Validates signer input for this policy.
     */
    public void requireSigners(Collection<String> signerIds) {
        List<String> distinct = signerIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        switch (this) {
            case confirm -> {
                if (distinct.isEmpty()) {
                    throw new IllegalStateException("confirm requires operator acknowledgment");
                }
            }
            case reauth -> {
                if (distinct.size() != 1) {
                    throw new IllegalStateException("reauth requires exactly one authenticated signer");
                }
            }
            case four_eyes -> {
                if (distinct.size() < 2) {
                    throw new IllegalStateException("four_eyes requires two distinct signer ids");
                }
            }
        }
    }
}
