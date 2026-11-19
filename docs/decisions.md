# Decisions

## To decide

- [ ] Widget content per size in detail
- [ ] Colours: the interim values (D7) and the final ones from the shared colour scheme "copper"
- [ ] App and widget names, and the launcher icon
- [ ] A push path for warnings
- [ ] The attribution text shown in the app

## Decisions

- D1 (2026-11-18): The app is its own standalone Android app with home-screen widgets and a small app; not a fork of an existing weather app, and not part of the Home Assistant app.
- D2 (2026-11-18): The app reads only from Home Assistant and keeps the last good answer as a local snapshot; the widgets draw from that snapshot.
- D3 (2026-11-18): Home Assistant delivers one sensor made for the app, the app sensor, with a versioned data format: its `schema_version` stays the same for additions and goes up for a field renamed or removed or a change of meaning. The app reads only that sensor, signed in as a normal (non-admin) user. The logic stays in Home Assistant; the app draws.
- D4 (2026-11-18): The first version has one widget whose content follows its size: small shows now and today's high and low; wide adds the next hours; tall adds the next days. A banner shows official warnings, and "Stand …" shows when the data is old. The app signs in and sets the fetch interval. A weather view of its own in the app comes later.
- D5 (2026-11-18): The widgets are RemoteViews, with drawn parts where text views cannot do it, as in copper-calendar; not Glance, which has no custom fonts and no canvas in Glance 1.2. Breezy Weather draws its trend widgets the same way.
- D6 (2026-11-18): Weather icons are animated in the widget and in the app, taken from the dashboard's icons and translated to AnimatedVectorDrawables. In a widget the icon is the indeterminate drawable of a ProgressBar: it runs endlessly while the widget is seen, and the platform stops it when it is not (another home page, an app on top, the screen off; measured on a Pixel 10 Pro with Android 17: 0 frames, 0 CPU). A setting per widget switches it to still images; it is set in Android's widget configuration, each widget set up on its own, as in copper-calendar. While the widget is seen the animation costs about +130 mA for the whole phone; accepted. A frame-by-frame ViewFlipper was tried and dropped, as it looks like a morph. A widget cannot cap its frame rate: `View.setRequestedFrameRate` is neither remotable nor settable by an XML attribute.
- ~~D7 (2026-11-18): Every colour the app shows comes from one place, the palette, the Material colour scheme included. The palette's role names follow the shared colour scheme "copper"; until its values exist, the palette holds interim values from the dashboard theme the weather card uses. Once the shared colour scheme's Android port ships a ready Material 3 colour scheme, the app takes that scheme instead of its own mapping.~~
- D7 (changed 2026-11-19): Every colour the app shows comes from one place, the palette, the Material colour scheme included. The palette's general roles (ground, surfaces, border, text, accent, warning) carry the general role names of the shared colour scheme "copper"; the domain colours (the weather scale, the water steps, rain and sun) keep the app's own names and later point at the scheme's named colours. Until the scheme's values exist, the palette holds interim values from the dashboard theme the weather card uses. Once the shared colour scheme's Android port ships a ready Material 3 colour scheme, the app takes that scheme instead of its own mapping.
- D8 (2026-11-18): Code shared with copper-calendar (sign-in, Home Assistant client, fetch, snapshot, widget basics) is copied and trimmed. A shared library is considered once both apps show which parts stay the same.
- D9 (2026-11-18): Minimum Android 12 (API 31), target the current API level (37), as copper-calendar: the app runs on phones that shipped with Android 12.
- D10 (2026-11-18): The setup follows copper-calendar: tasks through mise, Gradle dependency verification and lock files, CI on GitHub Actions with Android Lint, ktlint and detekt with warnings as errors, SonarQube Cloud, CodeQL, a check of the PR title and Dependabot. release-please for versions and the changelog follows romm-tender and emu-atlas.
