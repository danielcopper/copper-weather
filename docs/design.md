# Design

A skeleton that grows as the design does. What is decided stands in [decisions.md](decisions.md), what is open under
"To decide" there; this file holds only what the decisions leave to the design.

## Widget

Its content by size bucket (flat, small, wide, tall), the warning band, "Stand …" and a tap: D4, D11–D17. How it is drawn: D5; its sizes, "now",
the hours and the days in detail: D33–D37; the tap: D39; the picker's preview: D40.
Weather icons: D6; the still images and where they come from: D38.

## Data

The app sensor and the snapshot: D2, D3; the fetch, the snapshot's file and the schema version: D28–D30.

To fill: the sensor's data format.

## App

What the app does: D4, D18, D20. The sign-in screen: D22–D27, D32; its fetch lines and interval: D28.

To fill: the screens still to come.

## Colours

D7; the roles and their interim values are in `dev.cupr.weather.palette.Palette`, the mapping to Material's colour
scheme in `app/src/main/kotlin/dev/cupr/weather/app/WeatherTheme.kt`.

## Launcher icon

A placeholder: a sun with four rays behind a cloud, in the palette's sun on its surface colour, light and dark; the
same drawable is the monochrome layer for themed icons.
