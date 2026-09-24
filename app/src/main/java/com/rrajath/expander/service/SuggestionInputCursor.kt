package com.rrajath.expander.service

/** Accessibility text events can precede the editor's selection update. */
internal object SuggestionInputCursor {
    fun resolve(text: String, before: String?, from: Int, added: Int, removed: Int,
                selectionStart: Int, selectionEnd: Int): Int? {
        return changeEnd(text, before, from, added, removed)
            ?: selectionStart.takeIf { it == selectionEnd && it in 0..text.length }
    }

    fun changeEnd(text: String, before: String?, from: Int, added: Int, removed: Int): Int? {
        if (before != null && from >= 0 && added >= 0 && removed >= 0 &&
            from <= before.length && removed <= before.length - from &&
            from <= text.length && added <= text.length - from &&
            text.length - added == before.length - removed &&
            text.regionMatches(0, before, 0, from) &&
            text.regionMatches(from + added, before, from + removed, before.length - from - removed)) {
            return from + added
        }
        return null
    }
}
