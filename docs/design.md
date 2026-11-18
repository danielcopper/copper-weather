# Design

A skeleton that grows as the design does. What is decided stands in [decisions.md](decisions.md), what is open under
"To decide" there; this file holds only what the decisions leave to the design.

## Widget

Its content by size bucket, the warnings banner and "Stand …": D4. How it is drawn: D5. Weather icons: D6.

## Data

The app sensor and the snapshot: D2, D3.

To fill: the sensor's data format.

## App

What the app does: D4. Until signing in exists, its one screen says that the app is not set up yet.

To fill: the screens.

## Colours

D7; the roles and their interim values are in `dev.cupr.weather.palette.Palette`, the mapping to Material's colour
scheme in `app/src/main/kotlin/dev/cupr/weather/app/WeatherTheme.kt`.

## Launcher icon

A placeholder: a sun with four rays behind a cloud, in the palette's sun on its card colour, light and dark; the same
drawable is the monochrome layer for themed icons.
