# Design

A skeleton: what the decisions in [decisions.md](decisions.md) fix so far. Each section grows as its decisions land;
what is still open is listed under "To decide" there.

## Widget

One widget in the first version, drawn as RemoteViews (D4, D5). What it shows by size bucket:

| Size bucket | Content |
|---|---|
| small | now, today's high and low |
| wide | plus the next hours |
| tall | plus the next days (D4) |

On top of that the warnings banner and "Stand …" (D4).

## Weather icons

AnimatedVectorDrawables translated from the dashboard's icons; in a widget, the indeterminate drawable of a
ProgressBar (D6).

## Data

The snapshot of the app sensor's last good answer (D2, D3).

To fill: the sensor's data format.

## App

Sign-in and the fetch interval (D4); until signing in exists, a screen that says the app is not set up yet.

To fill: the screens.

## Colours

The roles and their interim values live in the palette (`dev.cupr.weather.palette.Palette`); the Material colour
scheme is mapped from it (D7).

## Launcher icon

A placeholder: a sun with four rays behind a cloud, in the palette's sun on its card surface, light and dark; the same
drawable is the monochrome layer for themed icons.
