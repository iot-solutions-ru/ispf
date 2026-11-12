package com.ispf.server.scada;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandPolicyTest {

    @Test
    void confirmRequiresAcknowledgment() {
        assertThrows(IllegalStateException.class, () -> CommandPolicy.confirm.requireSigners(List.of()));
        assertDoesNotThrow(() -> CommandPolicy.confirm.requireSigners(List.of("operator-a")));
    }

    @Test
    void reauthRequiresExactlyOneSigner() {
        assertThrows(IllegalStateException.class, () -> CommandPolicy.reauth.requireSigners(List.of()));
        assertThrows(IllegalStateException.class, () -> CommandPolicy.reauth.requireSigners(List.of("a", "b")));
        assertDoesNotThrow(() -> CommandPolicy.reauth.requireSigners(List.of("operator-a")));
    }

    @Test
    void fourEyesRequiresTwoDistinctSigners() {
        assertThrows(IllegalStateException.class, () -> CommandPolicy.four_eyes.requireSigners(List.of("a")));
        assertThrows(IllegalStateException.class, () -> CommandPolicy.four_eyes.requireSigners(List.of("a", "a")));
        assertDoesNotThrow(() -> CommandPolicy.four_eyes.requireSigners(List.of("signer-a", "signer-b")));
    }
}
