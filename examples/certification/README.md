# Certification exam question banks (BL-190)

Stub multiple-choice banks for ISPF certification tracks. Full LMS integration and proctoring ship with Phase 32 GA.

| File | Track | Level | Questions |
|------|-------|-------|-----------|
| [solution-developer-l1.json](solution-developer-l1.json) | Solution developer | L1 Foundation | 8 |
| [solution-developer-l2.json](solution-developer-l2.json) | Solution developer | L2 Automation | 6 |
| [platform-admin-core.json](platform-admin-core.json) | Platform admin | Core | 8 |

## Schema

```json
{
  "track": "solution-developer",
  "level": 1,
  "title": "...",
  "version": "0.1.0",
  "bl": "BL-190",
  "questions": [
    {
      "id": "sd-l1-q01",
      "type": "multiple-choice",
      "topic": "object-tree",
      "prompt": "...",
      "options": ["...", "..."],
      "correctIndex": 0,
      "reference": "docs/en/object-model.md"
    }
  ]
}
```

## Usage

- Study alignment: [docs/en/certification.md](../../docs/en/certification.md)
- Partner tiers: [docs/en/partner-program.md](../../docs/en/partner-program.md)
- Count questions: `jq '.questions | length' solution-developer-l1.json`

Practical lab exams remain separate (instructor-verified); these JSON banks cover knowledge checks only.
