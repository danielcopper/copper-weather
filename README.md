# copper-weather

Android home-screen widgets that show the weather from Home Assistant, with animated weather icons, and a small app to
sign in and set them up. Home Assistant prepares the data in one sensor made for the app; the app keeps its last good
answer and draws it.

## Status

The app signs in to Home Assistant on its one screen, fetches the app sensor at the interval set there and keeps its
last good answer; the widget "Wetter" shows it by its size: "now" with today's high and low and the rain hint, the next
hours from 4 × 2 cells, the next days from 4 × 3, or one row high "now" with the next days or hours beside it, with
animated weather icons; an official warning that applies shows as a band across the top, or one row high and at 2 × 2 in
short in the line below the temperature, and "Stand 14:30" says when the data is over an hour old; each widget has its
own settings (which icons move, days or hours one row high, the card's opacity); a tap opens the dashboard's weather
pop-up in the Home Assistant app. The decisions so far are in [docs/decisions.md](docs/decisions.md), the design in
[docs/design.md](docs/design.md), the terms in [GLOSSARY.md](GLOSSARY.md).

## Build and run

The tasks run through [mise](https://mise.jdx.dev/), which brings the JDK; the Android SDK goes to `~/Android/Sdk`
without root.

```sh
mise run setup    # Android command-line tools, SDK packages, local.properties
mise run gate     # most of what CI checks; mise.toml says what it leaves out
mise run install  # builds the debug APK and installs it with adb
```

The unit tests also run Python, which mise brings as well, to check the weather icons against the SVGs they are made
from, still and animated, and the warnings' symbols against the Material Symbols they are made from; `mise run icons`
and `mise run symbols` write them anew.

`install` needs a phone that adb is connected to: pair it once with `adb pair` (Wireless debugging on the phone), then
`adb connect <address>`; adb is in `~/Android/Sdk/platform-tools`.

Versions and the changelog come from [release-please](https://github.com/googleapis/release-please), from the
Conventional Commits on `main`.

## License

MIT, see [LICENSE](LICENSE). The libraries the app links (AndroidX, Kotlin and kotlinx, OkHttp and Okio, Guava's
ListenableFuture, JSpecify, JetBrains' annotations), the Gradle wrapper and the copies of Google's Material Symbols in
`icons/symbols` (Apache 2.0, its licence beside them), from which the warnings' symbols are made, keep their own
licenses.
