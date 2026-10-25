# Contribuer à Tilevio Launcher

Tilevio Launcher est développé et maintenu par une seule personne. Le projet
reste volontairement minimaliste (voir
[ADR 0001](docs/adr/0001-use-compose-for-tv.md)) : les pull requests externes
ne sont pas acceptées sans accord préalable.

## Signaler un bug ou proposer une idée

C'est la meilleure façon de contribuer. Ouvrez un ticket sur
[GitHub Issues](https://github.com/Raphael87-FR/tilevio-launcher/issues) avec :

- ce qui était attendu et ce qui s'est passé,
- les étapes pour reproduire,
- le modèle de TV ou de boîtier et la version d'Android si pertinent,
- une capture d'écran si utile.

Pour un sujet lié à la sécurité, n'ouvrez pas de ticket public : suivez
[SECURITY.md](SECURITY.md).

Utilisez le vocabulaire défini dans [GLOSSARY.md](GLOSSARY.md) (Launcher, App,
Tile, Wallpaper…).

## Mise en route

Prérequis : JDK 17 ou plus et le SDK Android (compile SDK 36).

```bash
./gradlew assembleDebug
```

## Vérifications avant de proposer un changement

```bash
./gradlew test assembleDebug
```

Ce sont les mêmes vérifications que la CI ([ci.yml](.github/workflows/ci.yml)).

## Convention de message de commit

Les messages de commit sont courts, descriptifs et en anglais, au format
[Conventional Commits](https://www.conventionalcommits.org/) :

```text
<type>: <description>
```

Types courants : `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `build`,
`ci`, `chore`, `revert`.

Pour un commit qui implémente un ticket, le message prend la forme
`implement closed #<n>: <description>`, avec `closed` juste après
`implement`, afin que GitHub ferme le ticket.

## Suivi du travail

Les tickets vivent sur GitHub Issues (voir
[docs/agents/issue-tracker.md](docs/agents/issue-tracker.md)).

## Étiquettes des tickets

Chaque ticket reçoit des étiquettes (labels) pour indiquer où il en est.
Celles de suivi, décrites dans
[docs/agents/triage-labels.md](docs/agents/triage-labels.md) :

- `needs-triage` — le mainteneur doit l'évaluer,
- `needs-info` — en attente d'informations de la part de l'auteur du ticket,
- `ready-for-agent` — entièrement spécifié, prêt à être traité par un agent,
- `ready-for-human` — à implémenter à la main,
- `wontfix` — ne sera pas traité.

Les étiquettes de type : `bug` (quelque chose ne fonctionne pas),
`enhancement` (nouvelle fonctionnalité), `documentation`, `accessibility`
(obstacle pour les personnes en situation de handicap), `question`,
`duplicate`, `invalid`, `good first issue` et `help wanted`.

## Licence

Voir [LICENSE](LICENSE).
