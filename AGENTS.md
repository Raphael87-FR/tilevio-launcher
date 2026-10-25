## Working principles

- State assumptions and ask when requirements are ambiguous.
- Keep the implementation minimal and scoped to the request; match the existing style.
- Keep unrelated code and formatting unchanged, and clean up code made unused by your changes.
- For multi-step work, define verifiable success criteria and run the project's standard checks before declaring completion.

## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues for `Raphael87-FR/tilevio-launcher`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles use their default label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Commit messages

Write commit messages in English, using scoped Conventional Commits subjects such as `feat(home): ...`, `fix(quiz): ...`, and `chore(build): ...`.
In commit messages, place `closed` immediately after `implement`.
