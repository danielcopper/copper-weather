package dev.cupr.weather.palette

import java.lang.reflect.Modifier

/** Every role's colour of this palette, by its field's name; Java reflection, as the app has no kotlin-reflect. */
fun Palette.colors(): Map<String, Int> =
    Palette::class.java.declaredFields
        .filter { it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
        .associate { field ->
            field.isAccessible = true
            field.name to field.getInt(this)
        }
