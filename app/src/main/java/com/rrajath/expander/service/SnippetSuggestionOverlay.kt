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
import android.text.TextUtils
import android.animation.ValueAnimator
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import com.rrajath.expander.data.Snippet

/** Content-first picker: an anchored reading list or one continuous horizontal rail. */
internal class SnippetSuggestionOverlay(
    private val service: AccessibilityService,
    private val onSelected: (Snippet) -> Unit
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private data class ContentKey(val snippets: List<Snippet>, val layout: SuggestionMenuLayout,
        val dark: Boolean, val ink: SuggestionResultColor, val width: Int, val fontScale: Float, val density: Float)
    private var contentKey: ContentKey? = null
    private var lastAnchor: Rect? = null
    private var lastSafeBounds: Rect? = null
    private var measuredContentHeight = 0
    val isShowing: Boolean get() = view != null
    private var lastParams: WindowManager.LayoutParams? = null
    private val readingFont = Typeface.DEFAULT
    private val fluidEase = PathInterpolator(0.16f, 1f, 0.3f, 1f)

    fun show(
        snippets: List<Snippet>,
        anchor: Rect,
        layout: SuggestionMenuLayout,
        colorMode: SuggestionColorMode,
        keyboardTop: Int? = null,
        resultColor: SuggestionResultColor = SuggestionResultColor.DEFAULT
    ) {
        if (snippets.isEmpty()) { dismiss(); return }

        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val metrics = windowManager.currentWindowMetrics
        val screenWidth = metrics.bounds.width()
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val safeLeft = insets.left + dp(8)
        val safeRight = screenWidth - insets.right - dp(8)
        val safeTop = insets.top + dp(8)
        val safeBottom = minOf(metrics.bounds.height() - insets.bottom, keyboardTop ?: Int.MAX_VALUE) - dp(8)
        if (safeRight <= safeLeft || safeBottom - safeTop < dp(48)) { dismiss(); return }
        val listWidth = minOf(dp(256), safeRight - safeLeft)
        // The rail deliberately spans the available screen width like an address bar.
        val railWidth = safeRight - safeLeft
        val width = if (layout == SuggestionMenuLayout.LIST) listWidth else railWidth
        val dark = colorMode == SuggestionColorMode.DARK ||
            (colorMode == SuggestionColorMode.AUTO && service.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        val key = ContentKey(snippets, layout, dark, resultColor, width,
            service.resources.configuration.fontScale, density)
        val safeBounds = Rect(safeLeft, safeTop, safeRight, safeBottom)
        // Watchdog calls must not measure, rebuild, or redraw an unchanged popup.
        if (view != null && key == contentKey && lastAnchor == anchor && lastSafeBounds == safeBounds) return
        val palette = paletteFor(if (dark) SuggestionColorMode.DARK else SuggestionColorMode.LIGHT)
        val surface = palette.surface
        val border = palette.border
        val primary = resultColor.argb(dark)
        val secondary = palette.secondary

        val container = if (key == contentKey && view != null) view as LinearLayout else LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            elevation = dp(8).toFloat()
            outlineAmbientShadowColor = Color.rgb(31, 46, 66)
            outlineSpotShadowColor = Color.rgb(31, 46, 66)
            background = FluidSuggestionSurface(surface, palette.base, border,
                dp(if (layout == SuggestionMenuLayout.LIST) 18 else 22).toFloat(), density * 0.5f)
            clipToOutline = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            defaultFocusHighlightEnabled = false
        }

        if (key != contentKey || view == null) {
        if (layout == SuggestionMenuLayout.LIST) {
            val rows = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
            snippets.forEachIndexed { index, snippet ->
                rows.addView(
                    listItem(snippet, primary, secondary, ::dp),
                    LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT)
                )
                if (index < snippets.lastIndex) {
                    rows.addView(View(service).apply {
                        setBackgroundColor((border and 0x00FFFFFF) or 0x38000000)
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, (density * 0.5f).toInt())).apply {
                        marginStart = dp(12)
                        marginEnd = dp(12)
                    })
                }
            }
            container.addView(ScrollView(service).apply {
                addView(rows)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }, LinearLayout.LayoutParams(-1, -1))
        } else {
            val cards = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), 0, dp(6), 0)
            }
            snippets.forEachIndexed { index, snippet ->
                cards.addView(
                    railItem(snippet, primary, secondary, ::dp),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                )
                if (index < snippets.lastIndex) {
                    cards.addView(TextView(service).apply {
                        text = "|"
                        setTextColor(secondary)
                        typeface = readingFont
                        textSize = 14f
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(14), dp(46)))
                }
            }
            container.addView(HorizontalScrollView(service).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(cards)
                isHorizontalFadingEdgeEnabled = true
                setFadingEdgeLength(dp(16))
            }, LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        }

        if (key != contentKey || view == null) {
            container.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            measuredContentHeight = container.measuredHeight.coerceAtLeast(dp(48))
        }
        val desiredHeight = measuredContentHeight
        val below = (safeBottom - anchor.bottom - dp(6)).coerceAtLeast(0)
        val above = (anchor.top - dp(6) - safeTop).coerceAtLeast(0)
        val placeBelow = below >= desiredHeight || below >= above
        val available = if (placeBelow) below else above
        val height = minOf(desiredHeight, dp(244), maxOf(dp(48), available), safeBottom - safeTop)
        val x = anchor.left.coerceIn(safeLeft, safeRight - width)
        val y = (if (placeBelow) anchor.bottom + dp(6) else anchor.top - height - dp(6))
            .coerceIn(safeTop, safeBottom - height)

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x
            this.y = y
        }
        try {
            val current = view as? LinearLayout
            if (current == null) {
                windowManager.addView(container, params)
                view = container
                if (ValueAnimator.areAnimatorsEnabled()) {
                    container.alpha = 0f
                    container.scaleX = 0.98f
                    container.scaleY = 0.96f
                    container.translationY = dp(if (placeBelow) -3 else 3).toFloat()
                    container.pivotX = (anchor.exactCenterX() - x).coerceIn(0f, width.toFloat())
                    container.pivotY = if (placeBelow) 0f else height.toFloat()
                    container.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                        .setInterpolator(fluidEase).setDuration(140).start()
                }
            } else {
                if (container !== current) {
                    current.removeAllViews()
                    while (container.childCount > 0) {
                        val child = container.getChildAt(0)
                        container.removeView(child)
                        current.addView(child)
                    }
                    current.background = container.background
                    current.setPadding(container.paddingLeft, container.paddingTop, container.paddingRight, container.paddingBottom)
                }
                if (lastParams?.let { it.x != x || it.y != y || it.width != width || it.height != height } != false) {
                    windowManager.updateViewLayout(current, params)
                }
            }
            contentKey = key
            lastParams = params
            lastAnchor = Rect(anchor)
            lastSafeBounds = safeBounds
        } catch (_: Exception) {
            dismiss()
        }
    }

    private fun listItem(snippet: Snippet, primary: Int, secondary: Int, dp: (Int) -> Int) = object : LinearLayout(service) {
        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            respondToPress(pressed)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = pressBackground(dp)
        contentDescription = "${snippet.expansion}, ${snippet.trigger}"
        setOnClickListener { onSelected(snippet) }
        addView(label(preview(snippet.expansion), primary, 14f, readingFont),
            LinearLayout.LayoutParams(-1, -2))
        addView(label(snippet.trigger, secondary, 10f, readingFont).apply {
            setPadding(0, dp(3), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun railItem(snippet: Snippet, primary: Int, secondary: Int, dp: (Int) -> Int) = object : TextView(service) {
        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            respondToPress(pressed)
        }
    }.apply {
        val expansion = preview(snippet.expansion)
        val trigger = "  ${snippet.trigger}"
        text = SpannableString(expansion + trigger).apply {
            setSpan(ForegroundColorSpan(primary), 0, expansion.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(secondary), expansion.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(0.78f), expansion.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        typeface = readingFont
        textSize = 14f
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = dp(320)
        minHeight = dp(48)
        background = pressBackground(dp)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(6), dp(12), dp(6))
        contentDescription = "${snippet.expansion}, ${snippet.trigger}"
        setOnClickListener { onSelected(snippet) }
    }

    private fun View.respondToPress(pressed: Boolean) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        animate().cancel()
        animate().scaleX(if (pressed) 0.975f else 1f).scaleY(if (pressed) 0.975f else 1f)
            .alpha(if (pressed) 0.88f else 1f).setDuration(if (pressed) 70 else 120)
            .setInterpolator(fluidEase).start()
    }

    private fun label(text: String, color: Int, size: Float, typeface: Typeface) = TextView(service).apply {
        this.text = text
        setTextColor(color)
        textSize = size
        this.typeface = typeface
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun pressBackground(dp: (Int) -> Int) = RippleDrawable(
        ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(0x24808080, Color.TRANSPARENT)),
        null,
        shape(Color.WHITE, Color.TRANSPARENT, dp(14), 0)
    )

    private data class Palette(val surface: Int, val base: Int, val border: Int, val primary: Int, val secondary: Int)

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
                surface = Color.rgb(47, 47, 50),
                base = Color.rgb(35, 35, 38),
                border = Color.rgb(101, 101, 107),
                primary = Color.rgb(248, 248, 249),
                secondary = Color.rgb(183, 183, 191)
            )
        } else {
            Palette(
                surface = Color.rgb(255, 255, 255),
                base = Color.rgb(244, 244, 247),
                border = Color.rgb(201, 201, 208),
                primary = Color.rgb(24, 32, 46),
                secondary = Color.rgb(105, 105, 114)
            )
        }
    }

    private fun shape(color: Int, stroke: Int, radius: Int, strokeWidth: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

    fun dismiss() {
        contentKey = null
        lastParams = null
        lastAnchor = null
        lastSafeBounds = null
        measuredContentHeight = 0
        val current = view ?: return
        current.animate().cancel()
        view = null
        try {
            windowManager.removeView(current)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    private fun preview(value: String): String {
        // Bound allocations even for very large snippets; never copy the entire expansion.
        val preview = StringBuilder(140)
        var pendingSpace = false
        for (char in value) {
            if (char.isWhitespace()) { pendingSpace = preview.isNotEmpty(); continue }
            if (preview.length >= 138) { preview.append('…'); break }
            if (pendingSpace) preview.append(' ')
            pendingSpace = false
            preview.append(char)
        }
        return preview.toString()
    }
}
