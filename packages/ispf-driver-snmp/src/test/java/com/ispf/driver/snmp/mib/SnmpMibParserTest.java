package com.ispf.driver.snmp.mib;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnmpMibParserTest {

    private static final String SAMPLE_MIB = """
            RFC1213-MIB DEFINITIONS ::= BEGIN
            IMPORTS
                DisplayString FROM RFC1213-MIB;

            iso OBJECT IDENTIFIER ::= { 1 }
            org OBJECT IDENTIFIER ::= { iso 3 }
            dod OBJECT IDENTIFIER ::= { org 6 }
            internet OBJECT IDENTIFIER ::= { dod 1 }
            mgmt OBJECT IDENTIFIER ::= { internet 2 }
            mib-2 OBJECT IDENTIFIER ::= { mgmt 1 }
            system OBJECT IDENTIFIER ::= { mib-2 1 }

            sysDescr OBJECT-TYPE
                SYNTAX      DisplayString (SIZE (0..255))
                MAX-ACCESS  read-only
                STATUS      current
                DESCRIPTION
                        "A textual description of the entity."
                ::= { system 1 }

            sysName OBJECT-TYPE
                SYNTAX      DisplayString (SIZE (0..255))
                MAX-ACCESS  read-write
                STATUS      current
                DESCRIPTION
                        "An administratively-assigned name."
                ::= { system 5 }

            sysUpTime OBJECT-TYPE
                SYNTAX      TimeTicks
                MAX-ACCESS  read-only
                STATUS      current
                DESCRIPTION
                        "Time since boot."
                ::= { system 3 }

            END
            """;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        SnmpMibLibrary.get().setRoot(tempDir);
    }

    @AfterEach
    void tearDown() {
        SnmpMibLibrary.get().clearForTests();
    }

    @Test
    void parsesObjectTypesAndResolvesOids() throws Exception {
        SnmpMibLibrary.get().importFile("RFC1213-MIB.mib", SAMPLE_MIB.getBytes(StandardCharsets.UTF_8));
        List<SnmpMibObject> objects = SnmpMibLibrary.get().listObjects();
        assertFalse(objects.isEmpty());
        SnmpMibObject sysName = objects.stream()
                .filter(o -> o.name().equals("sysName"))
                .findFirst()
                .orElseThrow();
        assertEquals("1.3.6.1.2.1.1.5", sysName.resolvedOid());
        assertEquals(SnmpMibObject.Kind.SCALAR, sysName.kind());
        assertTrue(sysName.maxAccess().toLowerCase().contains("read-write"));
    }

    @Test
    void proposeAddsInstanceZeroForScalars() throws Exception {
        SnmpMibLibrary.get().importFile("RFC1213-MIB.mib", SAMPLE_MIB.getBytes(StandardCharsets.UTF_8));
        var proposals = SnmpMibCatalogSupport.propose(List.of(
                new com.ispf.driver.DriverPointCatalog.PointSelection("object:RFC1213-MIB::sysName", "")
        ));
        assertEquals(1, proposals.size());
        assertEquals("sysName", proposals.getFirst().variableName());
        assertEquals("1.3.6.1.2.1.1.5.0:STRING", proposals.getFirst().pointAddress());
        assertEquals("snmpString", proposals.getFirst().schemaName());
        assertTrue(proposals.getFirst().writable());
    }

    @Test
    void mapValueKindRecognizesCounters() {
        assertEquals("INTEGER", SnmpMibCatalogSupport.mapValueKind("Counter32"));
        assertEquals("STRING", SnmpMibCatalogSupport.mapValueKind("DisplayString (SIZE (0..255))"));
        assertEquals("BOOLEAN", SnmpMibCatalogSupport.mapValueKind("TruthValue"));
    }

    @Test
    void classifiesTableColumnsFromEntryParent() throws Exception {
        String mib = """
                IF-MIB DEFINITIONS ::= BEGIN
                iso OBJECT IDENTIFIER ::= { 1 }
                org OBJECT IDENTIFIER ::= { iso 3 }
                dod OBJECT IDENTIFIER ::= { org 6 }
                internet OBJECT IDENTIFIER ::= { dod 1 }
                mgmt OBJECT IDENTIFIER ::= { internet 2 }
                mib-2 OBJECT IDENTIFIER ::= { mgmt 1 }
                interfaces OBJECT IDENTIFIER ::= { mib-2 2 }
                ifTable OBJECT-TYPE
                    SYNTAX      SEQUENCE OF IfEntry
                    MAX-ACCESS  not-accessible
                    STATUS      current
                    DESCRIPTION "A list of interface entries."
                    ::= { interfaces 2 }
                ifEntry OBJECT-TYPE
                    SYNTAX      IfEntry
                    MAX-ACCESS  not-accessible
                    STATUS      current
                    DESCRIPTION "An interface entry."
                    INDEX       { ifIndex }
                    ::= { ifTable 1 }
                ifInOctets OBJECT-TYPE
                    SYNTAX      Counter32
                    MAX-ACCESS  read-only
                    STATUS      current
                    DESCRIPTION "Octets received."
                    ::= { ifEntry 10 }
                END
                """;
        SnmpMibLibrary.get().importFile("IF-MIB.mib", mib.getBytes(StandardCharsets.UTF_8));
        SnmpMibObject ifInOctets = SnmpMibLibrary.get().listObjects().stream()
                .filter(o -> o.name().equals("ifInOctets"))
                .findFirst()
                .orElseThrow();
        assertEquals(SnmpMibObject.Kind.COLUMN, ifInOctets.kind());
        assertEquals("1.3.6.1.2.1.2.2.1.10", ifInOctets.resolvedOid());

        var proposals = SnmpMibCatalogSupport.propose(List.of(
                new com.ispf.driver.DriverPointCatalog.PointSelection("object:IF-MIB::ifInOctets", "2")
        ));
        assertEquals("ifInOctets_2", proposals.getFirst().variableName());
        assertEquals("1.3.6.1.2.1.2.2.1.10.2:INTEGER", proposals.getFirst().pointAddress());
    }
}
