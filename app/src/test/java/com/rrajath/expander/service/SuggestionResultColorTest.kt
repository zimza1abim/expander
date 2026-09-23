package com.rrajath.expander.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class SuggestionResultColorTest {
    @Test fun invalidStoredSelectionUsesDefault() {
        assertEquals(SuggestionResultColor.DEFAULT, SuggestionResultColor.fromStored(null))
        assertEquals(SuggestionResultColor.DEFAULT, SuggestionResultColor.fromStored("removed-color"))
        SuggestionResultColor.entries.forEach {
            assertEquals(it, SuggestionResultColor.fromStored(it.name))
        }
    }

    @Test fun resultColorsAreReadableAcrossBothSurfaceGradients() {
        for (color in SuggestionResultColor.entries) {
            for (dark in listOf(false, true)) {
                val surfaces = if (dark) listOf(0xFF2F2F32.toInt(), 0xFF232326.toInt())
                    else listOf(0xFFFFFFFF.toInt(), 0xFFF4F4F7.toInt())
                for (surface in surfaces) {
                    val foreground = luminance(color.argb(dark))
                    val background = luminance(surface)
                    val contrast = (maxOf(foreground, background) + 0.05) /
                        (minOf(foreground, background) + 0.05)
                    assertTrue("${color.name}, dark=$dark: $contrast", contrast >= 4.5)
                }
            }
        }
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
