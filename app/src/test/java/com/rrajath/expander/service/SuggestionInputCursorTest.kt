package com.rrajath.expander.service

import com.rrajath.expander.data.Snippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionInputCursorTest {
    @Test fun thirdCharacterMatchesEvenWhenSelectionStillSaysTwo() {
        val cursor = SuggestionInputCursor.resolve("ema", "em", 2, 1, 0, 2, 2)!!
        assertEquals(3, cursor)
        val token = SnippetSuggestionMatcher.tokenAtCursor("ema", cursor)!!
        assertEquals(1, SnippetSuggestionMatcher.find(
            listOf(Snippet(trigger = "email1", expansion = "example.com")), token.value, 3, 5).size)
    }

    @Test fun composingReplacementAndPasteUseTheirActualRange() {
        assertEquals(3, SuggestionInputCursor.resolve("한글입", "한글이", 2, 1, 1, 2, 2))
        assertEquals(3, SuggestionInputCursor.resolve("ema rest", "old rest", 0, 3, 3, 0, 3))
        assertEquals(4, SuggestionInputCursor.resolve("emai rest", "em rest", 2, 2, 0, 2, 2))
    }

    @Test fun deletionAndMiddleEditsKeepSuffixIntact() {
        assertEquals(3, SuggestionInputCursor.resolve("ema tail", "emai tail", 3, 0, 1, 4, 4))
        assertEquals(7, SuggestionInputCursor.resolve("abc ema tail", "abc em tail", 6, 1, 0, 6, 6))
    }

    @Test fun inconsistentEventsNeverInventACursor() {
        assertNull(SuggestionInputCursor.changeEnd("different", "em", 2, 1, 0))
        assertNull(SuggestionInputCursor.resolve("ema", null, -1, 1, 0, -1, -1))
        assertNull(SuggestionInputCursor.resolve("ema", null, -1, 1, 0, 0, 3))
        assertEquals(2, SuggestionInputCursor.resolve("ema", null, -1, 1, 0, 2, 2))
    }
}
