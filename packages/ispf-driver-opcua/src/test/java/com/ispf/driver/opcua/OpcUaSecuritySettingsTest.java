package com.ispf.driver.opcua;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpcUaSecuritySettingsTest {

    @Test
    void noneIsDefault() {
        OpcUaSecuritySettings settings = OpcUaSecuritySettings.parse(null, null, null);
        assertEquals(SecurityPolicy.None, settings.policy());
        assertEquals(MessageSecurityMode.None, settings.mode());
        assertFalse(settings.secure());
    }

    @Test
    void basic256Sha256DefaultsToSignAndEncrypt() {
        OpcUaSecuritySettings settings = OpcUaSecuritySettings.parse("Basic256Sha256", "", "/pki");
        assertEquals(SecurityPolicy.Basic256Sha256, settings.policy());
        assertEquals(MessageSecurityMode.SignAndEncrypt, settings.mode());
        assertTrue(settings.secure());
        assertEquals("/pki", settings.pkiDir());
    }

    @Test
    void signModeIsAccepted() {
        OpcUaSecuritySettings settings = OpcUaSecuritySettings.parse(
                "Aes128_Sha256_RsaOaep",
                "Sign",
                "d"
        );
        assertEquals(SecurityPolicy.Aes128_Sha256_RsaOaep, settings.policy());
        assertEquals(MessageSecurityMode.Sign, settings.mode());
    }

    @Test
    void nonePolicyForcesNoneMode() {
        OpcUaSecuritySettings settings = OpcUaSecuritySettings.parse("None", "SignAndEncrypt", "");
        assertEquals(MessageSecurityMode.None, settings.mode());
        assertFalse(settings.secure());
    }

    @Test
    void noneModeWithSecurePolicyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OpcUaSecuritySettings.parse("Basic256Sha256", "None", "d"));
    }

    @Test
    void unknownPolicyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OpcUaSecuritySettings.parse("NotAPolicy", "", ""));
    }
}
