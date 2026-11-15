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

From the repository root, after `npm install` in `tools/ispf-cli`:

```bash
node tools/ispf-cli/bin/ispf.mjs cert check examples/certification/solution-developer-l1.json
node tools/ispf-cli/bin/ispf.mjs cert run examples/certification/solution-developer-l1.json
```

`cert check` checks the file. `cert run` asks each question on a terminal (option numbers starting at 1). `--answers <file>` grades a JSON object of question id to the 0-based `correctIndex`. The command does not call a server. Practical labs stay outside this score.

Study notes: [docs/en/certification.md](../../docs/en/certification.md).
