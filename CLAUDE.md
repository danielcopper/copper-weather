# copper-weather

An Android app with home-screen widgets that show the weather from Home Assistant, with animated weather icons. Decisions are in [docs/decisions.md](docs/decisions.md), the design in [docs/design.md](docs/design.md), the terms in [GLOSSARY.md](GLOSSARY.md).

## Build and checks

- **Gate**: `mise run gate`; its description in `mise.toml` says what it covers. On a fresh machine `mise run setup` first.
- **Lint only**: `mise run lint`; ktlint, detekt and Android Lint run on the whole module, not on single files. Warnings are errors in all three and in the Kotlin compiler.
- **Dependency change**: `mise run deps-refresh` rewrites the lock files (`*.lockfile`) and `gradle/verification-metadata.xml`, which every build checks; commit them with the change after reading the diff.
- **Known build notice**: `mergeDebugUnitTestManifest` reports that `androidx.work.WorkManagerInitializer` "was tagged … to remove other declarations but no other declaration present". Leave it and don't report it (D28). It is a problem only if a built manifest under `app/build/intermediates` contains that initializer, or the app's own manifest step reports it too.
- **Weather icons**: generated, never edited by hand; `mise run icons` writes them from `icons/weather` (D38).
- **Versions**: libraries and Gradle plugins in `gradle/libs.versions.toml`, Gradle in `gradle/wrapper/gradle-wrapper.properties`, the JDK in `mise.toml` and CI's `JAVA_VERSION`, Python in `mise.toml` and CI's `PYTHON_VERSION`, the Android SDK packages and command-line tools in `mise.toml`'s `setup`, the GitHub Actions in the workflows; the reason for a version held back stands beside it.
- **App version**: `version.txt`, kept by release-please (`.github/workflows/release.yml`); the version code follows from it (`app/build.gradle.kts`). Never edit `version.txt` or `CHANGELOG.md` by hand.

## Tests

- Unit tests under `app/src/test`, JUnit 4 with Robolectric for anything that needs Android, Compose's test rule for the app's screen; `mise run test` runs them, the gate runs them with coverage.
- The weather icon script's own tests live in `scripts/` (Python's unittest); WeatherIconsTest runs them and the script's `--check` with mise's Python.

## Conventions

- Conventional Commits, lowercase, imperative.
- Code, comments and docs in English; the app's visible texts are German Android string resources, except its name.
- Comments name the decision behind a rule (`D7`), from `docs/decisions.md`.
- Colours: D7 and the KDoc of `dev.cupr.weather.palette.Palette`.

## Machine notes

- Tools come through mise or live under `$HOME`; the Android SDK is in `$HOME/Android/Sdk`, installed by `mise run setup`.
- Gradle uses four workers and a 2 GB heap (`gradle.properties`).
- Run the gate once; never start a second Gradle run in the same tree while one is still going.
- Absolute paths in every command (`git -C <worktree>`); the shell's working directory resets between calls.
- Scratch files go in the agent's scratchpad, never inside the worktree.
- Clean up only your own processes, by PID; stop the Gradle daemon with `./gradlew --stop`, never with `pkill -f`.

## Pipeline

- **Gate**: `mise run gate`; see CLAUDE.md's "Build and checks".
- **Review checks**: `mise run lint`, on the whole module (see CLAUDE.md's "Build and checks").
- **Agent rules**: CLAUDE.md's "Machine notes"; after a dependency change, `mise run deps-refresh` as CLAUDE.md's "Build and checks" says.
