package com.rrajath.expander.service

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.rrajath.expander.data.Snippet

/** Minimal suggestion picker: a short list or a horizontal row of compact cards. */
internal class SnippetSuggestionOverlay(
    private val service: AccessibilityService,
    private val onSelected: (Snippet) -> Unit
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show(
        snippets: List<Snippet>,
        anchor: Rect,
        layout: SuggestionMenuLayout,
        colorMode: SuggestionColorMode
    ) {
        dismiss()
        if (snippets.isEmpty()) return

        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val metrics = windowManager.currentWindowMetrics
        val screenWidth = metrics.bounds.width()
        val listWidth = dp(232)
        // The rail deliberately spans the available screen width like an address bar.
        val railWidth = (screenWidth - dp(16)).coerceAtLeast(dp(168))
        val width = if (layout == SuggestionMenuLayout.LIST) listWidth else railWidth
        val palette = paletteFor(colorMode)
        val surface = palette.surface
        val border = palette.border
        val primary = palette.primary
        val secondary = palette.secondary

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (layout == SuggestionMenuLayout.LIST) dp(4) else 0, dp(4), if (layout == SuggestionMenuLayout.LIST) dp(4) else 0, dp(4))
            if (layout == SuggestionMenuLayout.LIST) {
                elevation = dp(10).toFloat()
                background = shape(surface, border, dp(18), dp(1))
            }
        }

        if (layout == SuggestionMenuLayout.LIST) {
            snippets.forEachIndexed { index, snippet ->
                container.addView(
                    listItem(snippet, primary, secondary, ::dp),
                    LinearLayout.LayoutParams(width - dp(8), dp(44))
                )
                if (index < snippets.lastIndex) {
                    container.addView(View(service).apply {
                        setBackgroundColor(border)
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        marginStart = dp(12)
                        marginEnd = dp(12)
                    })
                }
            }
        } else {
            val cards = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), 0, dp(6), 0)
            }
            snippets.forEachIndexed { index, snippet ->
                cards.addView(
                    railItem(snippet, primary, secondary, ::dp),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46))
                )
                if (index < snippets.lastIndex) {
                    cards.addView(TextView(service).apply {
                        text = "|"
                        setTextColor(border)
                        textSize = 14f
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(14), dp(46)))
                }
            }
            container.addView(HorizontalScrollView(service).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(cards)
                background = shape(surface, border, dp(18), dp(1))
            }, LinearLayout.LayoutParams(width, dp(50)))
        }

        val keyboardTop = metrics.bounds.height() - metrics.windowInsets.getInsets(WindowInsets.Type.ime()).bottom
        val estimatedHeight = if (layout == SuggestionMenuLayout.LIST) {
            snippets.size * dp(45) + dp(8)
        } else {
            dp(50)
        }
        val x = anchor.left.coerceIn(dp(8), (screenWidth - width - dp(8)).coerceAtLeast(dp(8)))
        val y = when {
            anchor.bottom + estimatedHeight + dp(6) <= keyboardTop -> anchor.bottom + dp(6)
            anchor.top - estimatedHeight - dp(6) >= dp(8) -> anchor.top - estimatedHeight - dp(6)
            else -> (keyboardTop - estimatedHeight - dp(6)).coerceAtLeast(dp(8))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        try {
            windowManager.addView(container, params)
            view = container
        } catch (_: Exception) {
            view = null
        }
    }

    private fun listItem(snippet: Snippet, primary: Int, secondary: Int, dp: (Int) -> Int) = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = pressBackground(dp)
        contentDescription = "${snippet.trigger}: ${preview(snippet.expansion)}"
        setOnClickListener { onSelected(snippet) }
        addView(label(preview(snippet.expansion), primary, 14f, Typeface.DEFAULT_BOLD))
        addView(label(snippet.trigger, secondary, 11f, Typeface.DEFAULT))
    }

    private fun railItem(snippet: Snippet, primary: Int, secondary: Int, dp: (Int) -> Int) = TextView(service).apply {
        val expansion = preview(snippet.expansion)
        val trigger = "  ${snippet.trigger}"
        text = SpannableString(expansion + trigger).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, expansion.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(primary), 0, expansion.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(secondary), expansion.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textSize = 12f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        contentDescription = "${snippet.trigger}: ${preview(snippet.expansion)}"
        setOnClickListener { onSelected(snippet) }
    }

    private fun label(text: String, color: Int, size: Float, typeface: Typeface) = TextView(service).apply {
        this.text = text
        setTextColor(color)
        textSize = size
        this.typeface = typeface
        maxLines = 1
    }

    private fun pressBackground(dp: (Int) -> Int) = RippleDrawable(
        ColorStateList.valueOf(Color.rgb(84, 84, 89)),
        shape(Color.TRANSPARENT, Color.TRANSPARENT, dp(14), 0),
        null
    )

    private data class Palette(val surface: Int, val border: Int, val primary: Int, val secondary: Int)

    private fun paletteFor(mode: SuggestionColorMode): Palette {
        val isDark = when (mode) {
            SuggestionColorMode.DARK -> true
            SuggestionColorMode.LIGHT -> false
            SuggestionColorMode.AUTO -> {
                service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            }
        }
        return if (isDark) {
            Palette(
                surface = Color.rgb(38, 38, 40),
                border = Color.rgb(72, 72, 76),
                primary = Color.rgb(248, 248, 249),
                secondary = Color.rgb(174, 174, 180)
            )
        } else {
            Palette(
                surface = Color.rgb(250, 250, 252),
                border = Color.rgb(210, 210, 216),
                primary = Color.rgb(26, 26, 29),
                secondary = Color.rgb(102, 102, 110)
            )
        }
    }

    private fun shape(color: Int, stroke: Int, radius: Int, strokeWidth: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

    fun dismiss() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeView(current)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    private fun preview(value: String): String = value
        .replace('\n', ' ')
        .trim()
        .let { if (it.length <= 60) it else it.take(59) + "…" }
}
