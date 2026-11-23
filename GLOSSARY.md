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

The size class of a widget that decides what it shows: flat, one row high (**now**, and from 4 cells wide the
**days** or, as the widget is set, the **hours** beside it), small (**now**), wide (plus the **hours**) and tall (plus
the **days**) (D4); where each begins: D11.

### Snapshot

Home Assistant's last good answer, kept on the phone; the widgets draw from it (D2).

### App sensor

The one Home Assistant sensor made for the app, and the only thing the app reads from Home Assistant (D3). It carries
the weather in the shape the widgets need, so the logic stays in Home Assistant and the app draws.

### Schema version

The version of the **app sensor**'s data format, in its `schema_version` field (D3). An added field keeps the version;
a field renamed or removed, or a field whose meaning changes, raises it.

### Condition

Home Assistant's word for the weather of a moment or a day, as its weather entities state it: `clear-night`,
`cloudy`, `exceptional`, `fog`, `hail`, `lightning`, `lightning-rainy`, `partlycloudy`, `pouring`, `rainy`, `snowy`,
`snowy-rainy`, `sunny`, `windy`, `windy-variant`. The weather icon is chosen by it.

### Now / hours / days

The three parts of the weather the widget shows (D4): **now** is the weather of the moment with today's high and low,
in the flat size bucket also today's rain (D12, D36); **hours** are the next hours; **days** are the next days.

### Rain hint

A short note that rain is falling or due soon. Its colour is the palette's `rain`.

### Warning

An official weather warning of the DWD for the place the weather is for, shown on the widget while it applies (D15):
as the **warning band** in the wide and tall size buckets, as its **short form** in the small and flat ones (D47). Its
kind follows its event code (EC_II), its symbol the kind (D48).

### Warning band

The band across the top of the widget in the wide and tall size buckets that shows the warnings: the official headline
of the highest, "+N" for the others (D15, D47).
_Avoid_: banner

### Short form

A warning in the small and flat size buckets, in the place of "now"'s line: "Warnung" or "Unwetter" by its level, the
symbol of its kind and "+N", the word giving way to a warning sign where it does not fit (D47).

### Prototype

The first version of the app, built in slices (D21).

### Twilight

The transition between day and night around sunrise and sunset, as the app sensor reports it.

### Still image / animated icon

The two forms of a weather icon (D6). The **still image** is the icon without motion, a VectorDrawable generated from
the dashboard's still SVGs (D38). The **animated icon** is an AnimatedVectorDrawable generated from the dashboard's
animated SVGs (D43), the indeterminate drawable of a ProgressBar that runs it endlessly while the widget is seen; the
platform stops it when the widget is not seen. Which icons a widget animates is its **animation level** (D44): off,
only "now" (the default), or all, "now", the hours and the days.

### Widget settings

A widget's own settings, set on its **configuration screen**, which the launcher's widget settings open (D44): its
animation level, the days or the hours in the flat size bucket (D46), and its card's opacity (D45). A widget without
settings uses the defaults.

### Palette role

What a colour is for, such as `surface`, `text`, `accent`, `rain` or `scale1`: a field of
`dev.cupr.weather.palette.Palette`, which gives every role one colour for light and one for dark. Where the role names
come from: D7.
