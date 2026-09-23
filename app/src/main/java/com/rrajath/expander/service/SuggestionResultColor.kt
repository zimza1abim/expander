package com.rrajath.expander.service

/** Paired ink colors retain contrast on both popup surfaces. No per-snippet storage needed. */
enum class SuggestionResultColor(val label: String, private val light: Long, private val dark: Long) {
    DEFAULT("Default", 0xFF18202E, 0xFFF8F8F9),
    BLUE("Blue", 0xFF185ABD, 0xFF9CC5FF),
    TEAL("Teal", 0xFF006B63, 0xFF80DAD0),
    PURPLE("Purple", 0xFF7040AA, 0xFFD2B5FF),
    ROSE("Rose", 0xFFAD315E, 0xFFFFACCB),
    AMBER("Amber", 0xFF845000, 0xFFFFCE80);

    fun argb(isDark: Boolean): Int = (if (isDark) dark else light).toInt()

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
