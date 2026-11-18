# Design

A skeleton: what the decisions in [decisions.md](decisions.md) fix so far. Each section grows as its decisions land;
what is still open is listed under "To decide" there.

## Widget

One widget in the first version (D4), drawn as RemoteViews with drawn parts where text views cannot do it (D5). Its
content follows its size:

| Size | Content |
|---|---|
| small | now, and today's high and low |
| wide | as small, plus the next hours |
| tall | as small, plus the next days |

A banner shows official warnings, and "Stand …" shows when the data is old (D4).

To fill: the content per size in detail.

## Weather icons

Animated in the widget and in the app, translated from the dashboard's icons to AnimatedVectorDrawables (D6). In a
widget the icon is the indeterminate drawable of a ProgressBar, which the platform stops while the widget is not seen.
A setting switches the widgets to still images (D6).

To fill: whether that setting is one for the app or one per widget.

## Data

The app reads only the app sensor and draws the snapshot of its last good answer (D2, D3).

To fill: the sensor's data format.

## App

The app signs in with Home Assistant and sets the fetch interval (D4). Until signing in exists, its screen says that it
is not set up yet. A weather view of its own comes later.

To fill: the screens, and the attribution text the app shows.

## Colours

Every colour comes from the palette, by role, light and dark (D7): surface, text, muted text, rain, the intensity
scale in five steps, water in three, and the sun. The values are interim, from the dashboard theme the weather card
uses.

To fill: the final values from the shared colour scheme "copper".

## Launcher icon

A placeholder: a sun with four rays behind a cloud, in the palette's sun on its surface, light and dark, with a
monochrome layer for themed icons.

To fill: the icon, and the names of the app and the widget.
