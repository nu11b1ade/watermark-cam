# Domain Docs

This repo uses a single context for 水印相机（WatermarkCam）.

## Before exploring, read these

- **`GLOSSARY.md`** at the repo root for the product's vocabulary.
- **`docs/adr/`** for ADRs that touch the area you're about to work in.

If these files don't exist, proceed silently. The `domain-modeling` skill creates glossary entries and ADRs lazily when terms or decisions actually get resolved.

## File structure

```text
/
├── GLOSSARY.md
├── docs/
│   └── adr/
│       └── .gitkeep
└── app/
```

New ADRs belong in `docs/adr/` and use numbered filenames such as `0001-<decision-slug>.md`.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, or a test name), use the term as defined in `GLOSSARY.md`. Avoid synonyms the glossary explicitly rejects.

If a concept is missing, reconsider whether it belongs to this domain or note the gap for `domain-modeling`.

## Flag ADR conflicts

If your output contradicts an existing ADR, surface the conflict explicitly and identify the ADR and the reason to reopen its decision.
