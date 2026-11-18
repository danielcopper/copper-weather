# GLOSSARY.md — Copper Weather domain glossary

This file is a glossary. It defines the canonical meaning of project-specific terms so that conversations, issues, PRs,
and code stay aligned. It is _not_ a spec or design doc — the design lives in `docs/design.md`, and decisions live in
`docs/decisions.md`.

When a term resolves during a discussion, it gets added here. When a term's meaning changes, the entry gets rewritten —
not appended to.

## Terms

### Widget

A home-screen widget of the app, built from RemoteViews with drawn parts where text views cannot do it (D5). The first
version has one widget, whose content follows its **size bucket** (D4).

### Size bucket

The size class of a widget that decides what it shows: small (**now** and today's high and low), wide (plus the
**hours**) and tall (plus the **days**) (D4). The widget's size in cells falls into exactly one bucket; where one ends
and the next begins is still to be decided.

### Snapshot

Home Assistant's last good answer, kept on the phone; the widgets draw from it (D2).

### App sensor

The one Home Assistant sensor made for the app, and the only thing the app reads from Home Assistant (D3). It carries
the weather in the shape the widgets need, so the logic stays in Home Assistant and the app draws. Its entity id is not
decided yet; documents call it "the app sensor".

### Schema version

The version of the **app sensor**'s data format, in its `schema_version` field (D3). An added field keeps the version;
a field renamed or removed, or a field whose meaning changes, raises it.

### Condition

Home Assistant's word for the weather of a moment or a day, as its weather entities state it: `clear-night`,
`cloudy`, `exceptional`, `fog`, `hail`, `lightning`, `lightning-rainy`, `partlycloudy`, `pouring`, `rainy`, `snowy`,
`snowy-rainy`, `sunny`, `windy`, `windy-variant`. The weather icon is chosen by it.

### Now / hours / days

The three parts of the weather the widget shows (D4): **now** is the weather of the moment with today's high and low;
**hours** are the next hours; **days** are the next days.

### Rain hint

The short note that rain is falling or about to fall, as the dashboard shows it from the radar: "Regen jetzt" while it
rains, "Regen in 25 min" before rain that is due within two hours, otherwise nothing. Its colour is the palette's
`Rain`.

### Warning

An official weather warning for the place the weather is for, shown as a banner on the widget while it applies (D4).

### Twilight

The transition around sunset and sunrise, from the sun at +6° above the horizon down to −12° below it in the evening,
and back in the morning. A sensor in Home Assistant states it as `day`, `sunset`, `night` or `sunrise`, and the
dashboard's icon of the moment shows the day picture, the night picture or the transition between them.

### Still image / animated icon

The two forms of a weather icon (D6). The **animated icon** is an AnimatedVectorDrawable that runs endlessly while the
widget is seen; the platform stops it when the widget is not seen. The **still image** is the same icon without
motion, which a setting switches the widgets to.

### Palette token

The name of a colour by its role in the app and its widgets, such as `Surface`, `Text`, `Rain` or `Scale1`
(`dev.cupr.weather.palette.PaletteToken`). The palette gives every token one colour for light and one for dark, and
is the only place in the app that holds colours (D7).
