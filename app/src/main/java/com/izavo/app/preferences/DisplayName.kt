package com.izavo.app.preferences

/** Limit by Unicode code points so a supplementary character is never split. */
fun normalizeDisplayName(value: String): String {
    val trimmed = value.trim()
    val count = trimmed.codePointCount(0, trimmed.length).coerceAtMost(40)
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, count)).trim()
}

fun homeGreeting(hour: Int, name: String = ""): String {
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    return normalizeDisplayName(name).takeIf(String::isNotEmpty)?.let { "$greeting, $it" } ?: greeting
}
