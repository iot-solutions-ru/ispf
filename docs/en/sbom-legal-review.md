> **Language:** Canonical English. Russian edition: [ru/sbom-legal-review.md](../ru/sbom-legal-review.md).
> **Status:** Engineering legal review (2026-08-02). Not counsel opinion.

# ISPF commercial delivery — SBOM legal review

> **Date:** 2026-08-02
> **Verdict:** CONDITIONAL_GO
> **Type:** engineering-legal-inventory

Engineering inventory and risk classification for commercial delivery preparation. Not a law-firm opinion; counsel review required for contracts.

## Verdict

No GPL/commercial protocol libraries remain in driver packs (ISPF clean-room codecs). YARG/LGPL report path removed. Closed commercial ship still requires Enterprise platform-license.json (AGPL otherwise). Counsel still required for Spring/EPL transitive notices.

## Scope counts

| Inventory | Count |
|-----------|------:|
| npm (web-console lockfile) | 931 |
| Java (ispf-server runtimeClasspath) | 260 |
| Driver packs (catalog) | 63 |
| Driver packs in permissive profile | 63 |
| Driver packs restricted | 0 |
| Findings (high / medium / total) | 0 / 0 / 26 |

## Commercial delivery posture

- Platform: `AGPL-3.0-only (+ optional Enterprise dual-license)`
- Recommended driver profile: **permissive**
- Enterprise platform license required for closed commercial use: **yes**
- Application bundles: separate customer/vendor EULA

## Recommendations

- **R1 (P0):** Commercial closed distribution requires valid platform-license.json (Enterprise) under LICENSE-COMMERCIAL.md; without it AGPL network source-offer applies.
- **R2 (P1):** Attach LICENSE, NOTICE, third-party-notices, and these CycloneDX SBOMs to every binary/appliance delivery.
- **R3 (P1):** If appliance/VM includes PostgreSQL/Redis/NATS/Keycloak/etc., add infrastructure image SBOM/notices separately.
- **R4 (P2):** Protocol packs (BACnet/DLMS/IEC104/DNP3/IPMI/RADIUS/M-Bus) use ISPF-owned Apache-2.0 codecs — no GPL/StepFunc third-party stacks remain.
- **R5 (P2):** Report path is Apache POI only — YARG/JasperReports/docx4j removed from ispf-server.
- **R6 (P2):** Spring Boot transitive weak-copyleft (EPL logback/Jetty/Jakarta CPE) remains — retain notices; not GPL strong-copyleft.
- **R7 (P2):** Dual-licensed jSerialComm / UnboundID LDAP SDK: elect Apache-2.0 / Free Use in SBOM and notices.

## Restricted driver packs (not in permissive)

| Pack | License | Class |
|------|---------|-------|

## Notable server weak-copyleft / CPE

| Component | License | Class |
|-----------|---------|-------|
| `ch.qos.logback:logback-classic` | EPL-1.0 OR LGPL-2.1 | weak-copyleft |
| `ch.qos.logback:logback-core` | EPL-1.0 OR LGPL-2.1 | weak-copyleft |
| `jakarta.annotation:jakarta.annotation-api` | EPL 2.0 OR GPL2 w/ CPE | weak-copyleft |
| `jakarta.xml.bind:jakarta.xml.bind-api` | EPL-2.0 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `org.eclipse.angus:angus-activation` | EPL-2.0 | weak-copyleft |
| `jakarta.transaction:jakarta.transaction-api` | EPL 2.0 OR GPL2 w/ CPE | weak-copyleft |
| `org.aspectj:aspectjweaver` | EPL-2.0 | weak-copyleft |
| `com.github.jnr:jnr-posix` | EPL-2.0 OR GNU General Public License Version 2 OR GNU Lesser General Public License Version 2.1 | weak-copyleft |
| `javax.annotation:javax.annotation-api` | CDDL-1.1 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `jakarta.servlet.jsp:jakarta.servlet.jsp-api` | EPL 2.0 OR GPL2 w/ CPE | weak-copyleft |
| `jakarta.ws.rs:jakarta.ws.rs-api` | EPL-2.0 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `org.glassfish.jersey.core:jersey-server` | EPL 2.0 OR The GNU General Public License (GPL), Version 2, With Classpath Exception OR Apache License, 2.0 OR Modified BSD | weak-copyleft-cpe |
| `org.glassfish.hk2:osgi-resource-locator` | EPL-2.0 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `org.glassfish.hk2:hk2-locator` | EPL-2.0 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `org.glassfish.hk2.external:aopalliance-repackaged` | EPL-2.0 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `org.glassfish.hk2:hk2-api` | EPL-2.0 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `org.glassfish.hk2:hk2-utils` | EPL-2.0 OR GPL-2.0-with-classpath-exception | weak-copyleft-cpe |
| `org.eclipse.jetty:jetty-server` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-http` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-io` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-util` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-servlet` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-security` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-util-ajax` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-webapp` | EPL-2.0 | weak-copyleft |
| `org.eclipse.jetty:jetty-xml` | EPL-2.0 | weak-copyleft |

## Ship checklist

- [ ] LICENSE (AGPL) + LICENSE-COMMERCIAL (if Enterprise)
- [ ] NOTICE
- [ ] docs/en/third-party-notices.md (or packaged copy)
- [ ] build/sbom/web-console.cdx.json
- [ ] build/sbom/ispf-server-runtime.cdx.json
- [ ] build/sbom/driver-packs.cdx.json
- [ ] build/sbom/LEGAL-REVIEW.md
- [ ] Per driver pack: LICENSE + THIRD_PARTY-NOTICE.txt
- [ ] platform-license.json issued for customer installationId
- [ ] DriverPackProfile=permissive (or documented exception list)

## SBOM artifacts

- `web-console.cdx.json` — CycloneDX 1.5 (npm)
- `ispf-server-runtime.cdx.json` — CycloneDX 1.5 (Maven runtime)
- `driver-packs.cdx.json` — CycloneDX 1.5 (pack catalog)
- `legal-review.json` — machine-readable twin of this report

---

*Generated by `tools/license-audit/generate-sbom.mjs`.*
