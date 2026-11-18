# copper-weather

An Android app with home-screen widgets that show the weather from Home Assistant, with animated weather icons. Decisions are in [docs/decisions.md](docs/decisions.md), the design in [docs/design.md](docs/design.md), the terms in [GLOSSARY.md](GLOSSARY.md).

## Build and checks

- **Gate**: `mise run gate` (build, unit tests with coverage, Android Lint, ktlint, detekt): what CI checks, less the SonarQube Cloud gate, CodeQL, the check of the lock files and checksums, and the PR title. On a fresh machine `mise run setup` first.
- **Lint only**: `mise run lint`; ktlint, detekt and Android Lint run on the whole module, not on single files. Warnings are errors in all three and in the Kotlin compiler.
- **Dependency change**: `mise run deps-refresh` rewrites the lock files (`*.lockfile`) and `gradle/verification-metadata.xml`, which every build checks; commit them with the change after reading the diff. Versions live in `gradle/libs.versions.toml`, with the reason beside any version held back.
- **Version**: release-please writes it into `version.txt`; `app/build.gradle.kts` reads it and derives the version code. Never edit `version.txt` or `CHANGELOG.md` by hand.

## Tests

- Unit tests under `app/src/test`, JUnit 4 with Robolectric for anything that needs Android, Compose's test rule for the app's screen; `mise run test` runs them, the gate runs them with coverage.

## Conventions

- Conventional Commits, lowercase, imperative.
- Code, comments and docs in English; the app's visible texts are German Android string resources, except its name.
- Comments name the decision behind a rule (`D7`), from `docs/decisions.md`.
- Every colour lives in `dev.cupr.weather.palette.Palette`, by `PaletteToken`, light and dark (D7). No other source holds a colour literal; `ColorLiteralTest` fails on one. The launcher icon's colours in `values/colors.xml` and `values-night/colors.xml` mirror palette tokens, and a test keeps them equal.

## Machine notes

- Tools come through mise or live under `$HOME`; the Android SDK is in `$HOME/Android/Sdk`, installed by `mise run setup`.
- Gradle uses four workers and a 2 GB heap (`gradle.properties`).
- Run the gate once; never start a second Gradle run in the same tree while one is still going.
- Absolute paths in every command (`git -C <worktree>`); the shell's working directory resets between calls.
- Scratch files go in the agent's scratchpad, never inside the worktree.
- Clean up only your own processes, by PID; stop the Gradle daemon with `./gradlew --stop`, never with `pkill -f`.
- `homelab` is read only: the dashboard and theme the app follows live there, and this repository never writes into it.
