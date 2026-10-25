# Contributing to Tilevio Launcher

Tilevio Launcher is built and maintained by a single person. The project is
deliberately minimalist (see
[ADR 0001](docs/adr/0001-use-compose-for-tv.md)): external pull requests are
not accepted without prior agreement.

## Reporting a bug or suggesting an idea

This is the best way to contribute. Open a ticket on
[GitHub Issues](https://github.com/Raphael87-FR/tilevio-launcher/issues) with:

- what you expected and what happened,
- the steps to reproduce,
- the TV or streaming box model and the Android version, if relevant,
- a screenshot if useful.

For anything security-related, do not open a public issue: follow
[SECURITY.md](SECURITY.md).

Use the vocabulary defined in [GLOSSARY.md](GLOSSARY.md) (Launcher, App,
Tile, Wallpaper…).

## Getting started

Requirements: JDK 17 or later and the Android SDK (compile SDK 36).

```bash
./gradlew assembleDebug
```

## Checks before proposing a change

```bash
./gradlew test assembleDebug
```

These are the same checks as the CI ([ci.yml](.github/workflows/ci.yml)).

## Commit message convention

Commit messages are short, descriptive and in English, following
[Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>: <description>
```

Common types: `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`,
`ci`, `chore`, `revert`.

A commit that implements an issue uses the form
`implement closed #<n>: <description>`, with `closed` right after
`implement`, so that GitHub closes the issue.

## Tracking work

Issues live on GitHub Issues (see
[docs/agents/issue-tracker.md](docs/agents/issue-tracker.md)).

## Issue labels

Each issue gets labels showing where it stands. The triage labels, described
in [docs/agents/triage-labels.md](docs/agents/triage-labels.md):

- `needs-triage`: the maintainer needs to evaluate it,
- `needs-info`: waiting on the reporter for more information,
- `ready-for-agent`: fully specified, ready for an agent to pick up,
- `ready-for-human`: requires human implementation,
- `wontfix`: will not be actioned.

The type labels: `bug` (something isn't working), `enhancement` (new
feature), `documentation`, `accessibility` (barrier for people with
disabilities), `question`, `duplicate`, `invalid`, `good first issue` and
`help wanted`.

## License

See [LICENSE](LICENSE).
