# copper-weather

Android home-screen widgets that show the weather from Home Assistant, with animated weather icons, and a small app to
sign in and set them up. Home Assistant prepares the data in one sensor made for the app; the app keeps its last good
answer and draws it.

## Status

The app signs in to Home Assistant on its one screen, fetches the app sensor at the interval set there and keeps its
last good answer; the widget "Wetter" shows its temperature and condition as plain text, a stand-in until the drawn
widget. The decisions so far are in [docs/decisions.md](docs/decisions.md), the design in
[docs/design.md](docs/design.md), the terms in [GLOSSARY.md](GLOSSARY.md).

## Build and run

The tasks run through [mise](https://mise.jdx.dev/), which brings the JDK; the Android SDK goes to `~/Android/Sdk`
without root.

```sh
mise run setup    # Android command-line tools, SDK packages, local.properties
mise run gate     # most of what CI checks; mise.toml says what it leaves out
mise run install  # builds the debug APK and installs it with adb
```

`install` needs a phone that adb is connected to: pair it once with `adb pair` (Wireless debugging on the phone), then
`adb connect <address>`; adb is in `~/Android/Sdk/platform-tools`.

Versions and the changelog come from [release-please](https://github.com/googleapis/release-please), from the
Conventional Commits on `main`.

## License

MIT, see [LICENSE](LICENSE). The libraries the app links (AndroidX, Kotlin and kotlinx, OkHttp and Okio, Guava's
ListenableFuture, JSpecify, JetBrains' annotations) and the Gradle wrapper keep their own licenses.
