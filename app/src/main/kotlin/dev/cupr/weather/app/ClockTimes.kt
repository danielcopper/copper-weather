package dev.cupr.weather.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A time of day as the app's screen and "Stand" show it: "14:30" (D16, D28). */
val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Whether [at] falls on the day of [now] in [zone], so a time shows without its date (D16, D28). */
fun sameDay(
    at: Instant,
    now: Instant,
    zone: ZoneId,
): Boolean = at.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
