package com.rrajath.expander.service

import com.rrajath.expander.data.Snippet

internal data class TypedToken(
    val value: String,
    val start: Int,
    val end: Int
)

internal object SnippetSuggestionMatcher {
    fun tokenAtCursor(text: String, cursor: Int): TypedToken? {
        if (cursor !in 0..text.length || cursor == 0) return null

        var start = cursor
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        if (start == cursor) return null

        return TypedToken(text.substring(start, cursor), start, cursor)
    }

    fun find(
        snippets: List<Snippet>,
        token: String,
        minimumLength: Int,
        maximumResults: Int
    ): List<Snippet> {
        if (token.length < minimumLength || maximumResults <= 0) return emptyList()

        return snippets.asSequence()
            .filter { it.trigger.length > token.length }
            .filter { it.trigger.startsWith(token, ignoreCase = true) }
            .sortedWith(compareBy<Snippet> { it.trigger.length }.thenBy { it.trigger.lowercase() })
            .take(maximumResults)
            .toList()
    }
}
