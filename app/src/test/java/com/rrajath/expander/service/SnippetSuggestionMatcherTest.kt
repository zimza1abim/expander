package com.rrajath.expander.service

import com.rrajath.expander.data.Snippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnippetSuggestionMatcherTest {
    @Test
    fun `token is taken from the cursor rather than the end of the field`() {
        assertEquals(TypedToken("zzema", 6, 11), SnippetSuggestionMatcher.tokenAtCursor("hello zzema world", 11))
    }

    @Test
    fun `whitespace at cursor has no token`() {
        assertNull(SnippetSuggestionMatcher.tokenAtCursor("hello ", 6))
    }

    @Test
    fun `prefix matches exclude exact trigger and respect limit`() {
        val snippets = listOf(
            Snippet(trigger = "zzemail-long", expansion = "three"),
            Snippet(trigger = "zzemail2", expansion = "two"),
            Snippet(trigger = "zzema", expansion = "exact"),
            Snippet(trigger = "zzemail1", expansion = "one")
        )

        assertEquals(
            listOf("zzemail1", "zzemail2"),
            SnippetSuggestionMatcher.find(snippets, "ZZEMA", 3, 2).map { it.trigger }
        )
    }

    @Test
    fun `minimum length suppresses matches`() {
        val snippets = listOf(Snippet(trigger = "zzemail", expansion = "one"))
        assertEquals(emptyList<Snippet>(), SnippetSuggestionMatcher.find(snippets, "zz", 3, 5))
    }

    @Test
    fun `cached ranking preserves prefix-only results and limits`() {
        val snippets = listOf(
            Snippet(trigger = "opengpt", expansion = "not a prefix"),
            Snippet(trigger = "gpt10", expansion = "long"),
            Snippet(trigger = "GPT2", expansion = "second"),
            Snippet(trigger = "gpt1", expansion = "first"),
            Snippet(trigger = "gpt", expansion = "exact")
        )
        val ranked = SnippetSuggestionMatcher.ranked(snippets)
        assertEquals(listOf("gpt1", "GPT2"),
            SnippetSuggestionMatcher.findRanked(ranked, "gpt", 3, 2).map { it.trigger })
        assertEquals(emptyList<Snippet>(), SnippetSuggestionMatcher.findRanked(ranked, "gp", 3, 2))
        assertEquals(emptyList<Snippet>(), SnippetSuggestionMatcher.findRanked(ranked, "gpt", 3, 0))
    }
}
