# G-01 pen-test — vendor outreach draft

> **Status:** Ready to send. Does **not** close G-01 until a dated third-party report + retest letter exist.  
> Pack: [pen-test-vendor-pack.md](pen-test-vendor-pack.md) · Parked: [parked-backlog.md](parked-backlog.md) **P-PENTEST**.

Fill `{{…}}` before sending. Attach the ordered artifacts from the vendor pack §1 (or zip the linked markdowns). Do **not** put passwords/SSH keys in email — vault / separate channel after NDA.

---

## Subject

`ISPF platform — grey-box pen-test RFP (G-01) — {{COMPANY}}`

## Body (EN)

Hello {{VENDOR_CONTACT}},

We would like a **grey-box application penetration test** of the ISPF industrial IoT platform on our private lab, with a written report (CVSS) and a **retest letter** for Critical/High findings.

**Indicative scope (see attached SOW):** Authn/Z and RBAC, Web Console + WebSocket ACL, IDOR on object/historian APIs, limited black-box recon. White-box source review is an optional add-on. Multi-tenant fixtures are **out of scope** unless we provision them before kickoff.

**Lab (snapshot — refresh at freeze):**  
- Reachability: jump host → lab VLAN (details in inventory; credentials via vault after NDA)  
- App: ISPF **{{VERSION}}** HTTP console/API (OpenAPI not published on lab)  
- Auth: local login; MFA may be disabled on lab — call out accepted risk or we enable before the window  

**Attachments (prep pack):** vendor cover, scope/SOW draft, RoE, questionnaire, case catalog, operator prep, lab inventory + 2026-09-09 preflight, findings/case-results templates, security overview.

Please reply with:  
1) confirmation you can work under our RoE,  
2) fixed-price or T&M quote for the default mix in the pack,  
3) earliest kickoff week,  
4) filled questionnaire (or questions blocking a bid).

Regards,  
{{SENDER_NAME}}  
{{SENDER_ROLE}} · {{COMPANY}}  
{{CONTACT_EMAIL}}

---

## Operator checklist before send

| Step | Done |
|------|------|
| NDA / DPA path agreed with legal | ☐ |
| Vendor short-list (1–3 firms) | ☐ |
| Zip or share links to pack §1 artifacts | ☐ |
| Confirm lab ISPF up for proposed window (OT soak timers are **stopped** — not a blocker) | ☐ |
| Decide MFA on/off for assessment window | ☐ |
| After award: version freeze + archive redacted report under `docs/evidence/security-pentest/YYYY-MM-DD-*/` | ☐ |
