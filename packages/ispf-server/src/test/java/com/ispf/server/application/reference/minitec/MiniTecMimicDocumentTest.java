package com.ispf.server.application.reference.minitec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniTecMimicDocumentTest {

    @Test
    void zoneMimicsIncludeSharedCustomSymbols() {
        assertTrue(MiniTecMimicDocument.ZONE_GAS_JSON.contains("\"id\":\"lib-data-block\""));
        assertTrue(MiniTecMimicDocument.ZONE_ELECTRICAL_JSON.contains("\"id\":\"lib-load-block\""));
        assertTrue(MiniTecMimicDocument.ZONE_GAS_JSON.length() > 10_000);
    }
}
