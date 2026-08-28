package com.rrajath.expander.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowInsets
import android.view.WindowManager
import com.rrajath.expander.data.AppDatabase
import com.rrajath.expander.data.Snippet
import com.rrajath.expander.data.SnippetRepository
import kotlinx.coroutines.*

class TextExpansionService : AccessibilityService() {

    private lateinit var repository: SnippetRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var snippetsCache: List<Snippet> = emptyList()
    private var lastProcessedText = ""
    private lateinit var suggestionOverlay: SnippetSuggestionOverlay
    private var suggestionTarget: SuggestionTarget? = null
    private var activeWindowCheck: Job? = null

    // Undo tracking
    private var lastExpansion: ExpansionHistory? = null

    private data class ExpansionHistory(
        val trigger: String,
        val expansion: String,
        val textBeforeTrigger: String
    )

    private data class SuggestionTarget(
        val node: AccessibilityNodeInfo,
        val token: TypedToken
    )

    companion object {
        private const val PREFS_NAME = "expander_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_SMART_PUNCTUATION_ENABLED = "smart_punctuation_enabled"
        private const val KEY_SMART_PUNCTUATION_CHARS = "smart_punctuation_chars"
        private const val KEY_PARTIAL_SUGGESTIONS_ENABLED = "partial_suggestions_enabled"
        private const val KEY_PARTIAL_SUGGESTIONS_MIN_LENGTH = "partial_suggestions_min_length"
        private const val KEY_PARTIAL_SUGGESTIONS_MAX_RESULTS = "partial_suggestions_max_results"
        private const val KEY_SUGGESTION_MENU_LAYOUT = "suggestion_menu_layout"
        private const val KEY_SUGGESTION_COLOR_MODE = "suggestion_color_mode"

        const val DEFAULT_PARTIAL_SUGGESTIONS_MIN_LENGTH = 3
        const val DEFAULT_PARTIAL_SUGGESTIONS_MAX_RESULTS = 5
        // Punctuation that should never have a space before it. If the keyboard's
        // symbol popup inserts one of these after a trailing space, the space is
        // removed and moved to after the punctuation instead. User-configurable via
        // Settings as a space-separated string; this is only the default.
        const val DEFAULT_SMART_PUNCTUATION_CHARS = "? ! , . ; : ) \" ' ] } ` ~"

        fun isServiceEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVICE_ENABLED, true)
        }

        fun setServiceEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_ENABLED, enabled)
                .apply()
        }

        fun isSmartPunctuationEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SMART_PUNCTUATION_ENABLED, true)
        }

        fun setSmartPunctuationEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SMART_PUNCTUATION_ENABLED, enabled)
                .apply()
        }

        /** Raw, space-separated string as edited by the user (may contain in-progress typing). */
        fun getSmartPunctuationCharsRaw(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SMART_PUNCTUATION_CHARS, DEFAULT_SMART_PUNCTUATION_CHARS)
                ?: DEFAULT_SMART_PUNCTUATION_CHARS
        }

        fun setSmartPunctuationCharsRaw(context: Context, raw: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SMART_PUNCTUATION_CHARS, raw)
                .apply()
        }

        fun arePartialSuggestionsEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PARTIAL_SUGGESTIONS_ENABLED, false)

        fun setPartialSuggestionsEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_PARTIAL_SUGGESTIONS_ENABLED, enabled).apply()
        }

        fun getPartialSuggestionsMinLength(context: Context): Int =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PARTIAL_SUGGESTIONS_MIN_LENGTH, DEFAULT_PARTIAL_SUGGESTIONS_MIN_LENGTH)
                .coerceIn(1, 20)

        fun setPartialSuggestionsMinLength(context: Context, value: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_PARTIAL_SUGGESTIONS_MIN_LENGTH, value.coerceIn(1, 20)).apply()
        }

        fun getPartialSuggestionsMaxResults(context: Context): Int =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_PARTIAL_SUGGESTIONS_MAX_RESULTS, DEFAULT_PARTIAL_SUGGESTIONS_MAX_RESULTS)
                .coerceIn(1, 10)

        fun setPartialSuggestionsMaxResults(context: Context, value: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_PARTIAL_SUGGESTIONS_MAX_RESULTS, value.coerceIn(1, 10)).apply()
        }

        fun getSuggestionMenuLayout(context: Context): SuggestionMenuLayout {
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SUGGESTION_MENU_LAYOUT, SuggestionMenuLayout.LIST.name)
            return runCatching { SuggestionMenuLayout.valueOf(stored ?: "") }
                .getOrDefault(SuggestionMenuLayout.LIST)
        }

        fun setSuggestionMenuLayout(context: Context, layout: SuggestionMenuLayout) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_SUGGESTION_MENU_LAYOUT, layout.name).apply()
        }

        fun getSuggestionColorMode(context: Context): SuggestionColorMode {
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SUGGESTION_COLOR_MODE, SuggestionColorMode.AUTO.name)
            return runCatching { SuggestionColorMode.valueOf(stored ?: "") }
                .getOrDefault(SuggestionColorMode.AUTO)
        }

        fun setSuggestionColorMode(context: Context, mode: SuggestionColorMode) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_SUGGESTION_COLOR_MODE, mode.name).apply()
        }

        /** Parses the raw space-separated string into single-character tokens, ignoring the rest. */
        fun parseSmartPunctuationChars(raw: String): Set<Char> {
            return raw.split(" ", "\t", "\n")
                .filter { it.length == 1 }
                .map { it[0] }
                .toSet()
        }

        /**
         * Checks if the accessibility service is actually enabled in system settings.
         * This is different from isServiceEnabled which only checks our internal preference.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${TextExpansionService::class.java.name}"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(expectedComponentName) == true
        }
    }

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(applicationContext)
        repository = SnippetRepository(database.snippetDao())
        suggestionOverlay = SnippetSuggestionOverlay(this, ::insertSuggestion)

        // Load snippets into cache
        serviceScope.launch {
            repository.getEnabledSnippets().collect { snippets ->
                snippetsCache = snippets
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isServiceEnabled(this)) {
            dismissSuggestions()
            return
        }

        // An accessibility overlay belongs to the current app window only. Close it
        // only after confirming the active input window really changed. Samsung emits
        // delayed window-state events for the overlay and IME themselves, so an event
        // alone must never be treated as proof that the user changed apps.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            verifySuggestionWindowAfterTransition()
            return
        }

        // Text changes are the only events that can create or refresh suggestions.
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val source = event.source ?: return

        try {
            val currentText = source.text?.toString() ?: ""

            // Check for backspace undo
            if (shouldUndoExpansion(currentText)) {
                undoExpansion(source)
                lastProcessedText = currentText
                return
            }

            val historyBefore = lastExpansion
            val fullExpandedText = historyBefore?.let { it.textBeforeTrigger + it.expansion }

            when {
                // Our own ACTION_SET_TEXT echo right after an expansion. Ignore it so
                // we neither re-expand the result nor disturb the undo history.
                fullExpandedText != null && currentText == fullExpandedText -> dismissSuggestions()

                // Text ends with a space: candidate for expansion.
                currentText.endsWith(" ") && currentText.isNotEmpty() -> {
                    dismissSuggestions()
                    if (!applySmartPunctuationSpacing(source, currentText)) {
                        processTextForExpansion(source, currentText)
                    }
                }

                else -> updateSuggestions(source, currentText)
            }

            // Once the user edits past the freshly-inserted expansion, the undo
            // history is stale; drop it so it can't mis-fire on a later edit. Skip
            // this when the block above just recorded a new expansion.
            if (lastExpansion === historyBefore) {
                lastExpansion?.let { history ->
                    if (ExpansionUndo.isHistoryStale(currentText, history.textBeforeTrigger, history.expansion)) {
                        lastExpansion = null
                    }
                }
            }

            lastProcessedText = currentText
        } catch (e: Exception) {
            // Silently handle errors to avoid service crashes
        } finally {
            source.recycle()
        }
    }

    private fun updateSuggestions(source: AccessibilityNodeInfo, text: String) {
        if (!arePartialSuggestionsEnabled(this) || !source.isEditable) {
            dismissSuggestions()
            return
        }

        val cursor = source.textSelectionStart.takeIf { it in 0..text.length } ?: text.length
        val token = SnippetSuggestionMatcher.tokenAtCursor(text, cursor)
        if (token == null) {
            dismissSuggestions()
            return
        }

        val matches = SnippetSuggestionMatcher.find(
            snippets = snippetsCache,
            token = token.value,
            minimumLength = getPartialSuggestionsMinLength(this),
            maximumResults = getPartialSuggestionsMaxResults(this)
        )
        if (matches.isEmpty()) {
            dismissSuggestions()
            return
        }

        replaceSuggestionTarget(AccessibilityNodeInfo.obtain(source), token)
        val bounds = Rect().also(source::getBoundsInScreen)
        suggestionOverlay.show(
            matches,
            bounds,
            getSuggestionMenuLayout(this),
            getSuggestionColorMode(this)
        )
    }

    private fun verifySuggestionWindowAfterTransition() {
        if (suggestionTarget == null) return
        activeWindowCheck?.cancel()
        activeWindowCheck = serviceScope.launch {
            // Let Android finish replacing the active window before comparing IDs.
            delay(150)
            val target = suggestionTarget ?: return@launch
            val insets = getSystemService(WindowManager::class.java)
                .currentWindowMetrics.windowInsets
            if (!insets.isVisible(WindowInsets.Type.ime())) {
                dismissSuggestions()
                return@launch
            }
            val activeRoot = rootInActiveWindow ?: return@launch
            try {
                if (activeRoot.windowId != target.node.windowId) {
                    dismissSuggestions()
                }
            } finally {
                activeRoot.recycle()
            }
        }
    }

    private fun insertSuggestion(snippet: Snippet) {
        val target = suggestionTarget ?: return
        suggestionOverlay.dismiss()

        try {
            if (!target.node.refresh()) return
            val currentText = target.node.text?.toString() ?: return
            val currentToken = SnippetSuggestionMatcher.tokenAtCursor(
                currentText,
                target.node.textSelectionStart.takeIf { it in 0..currentText.length } ?: target.token.end
            ) ?: return

            // Refuse to write if the app changed the field while the overlay was visible.
            if (currentToken.start != target.token.start ||
                !currentToken.value.equals(target.token.value, ignoreCase = false)
            ) return

            val expansion = SnippetProcessor.process(snippet.expansion)
            val newText = currentText.replaceRange(currentToken.start, currentToken.end, expansion)
            val newCursor = currentToken.start + expansion.length
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            if (target.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                arguments.clear()
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                target.node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)

                if (currentToken.end == currentText.length) {
                    lastExpansion = ExpansionHistory(
                        trigger = currentToken.value,
                        expansion = expansion,
                        textBeforeTrigger = currentText.substring(0, currentToken.start)
                    )
                }
            }
        } catch (_: Exception) {
            // The target app may invalidate its accessibility node at any time.
        } finally {
            clearSuggestionTarget()
        }
    }

    private fun replaceSuggestionTarget(node: AccessibilityNodeInfo, token: TypedToken) {
        clearSuggestionTarget()
        suggestionTarget = SuggestionTarget(node, token)
    }

    private fun clearSuggestionTarget() {
        suggestionTarget?.node?.recycle()
        suggestionTarget = null
    }

    private fun dismissSuggestions() {
        activeWindowCheck?.cancel()
        activeWindowCheck = null
        if (::suggestionOverlay.isInitialized) suggestionOverlay.dismiss()
        clearSuggestionTarget()
    }

    private fun shouldUndoExpansion(currentText: String): Boolean {
        val history = lastExpansion ?: return false
        return ExpansionUndo.shouldUndo(currentText, history.textBeforeTrigger, history.expansion)
    }

    private fun undoExpansion(source: AccessibilityNodeInfo) {
        val history = lastExpansion ?: return

        try {
            // Restore the original trigger text
            val restoredText = history.textBeforeTrigger + history.trigger

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, restoredText)
            }

            source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Move cursor to the end
            arguments.clear()
            arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                restoredText.length
            )
            arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                restoredText.length
            )
            source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)

            // Clear the undo history after using it
            lastExpansion = null
        } catch (e: Exception) {
            // Silently handle errors
        }
    }

    /**
     * Fixes text like "how are you ? " (space typed before punctuation, e.g. via the
     * keyboard's long-press symbol popup) into "how are you? " by moving the space(s)
     * from before the punctuation to after it. Returns true if a fix was applied.
     */
    private fun applySmartPunctuationSpacing(source: AccessibilityNodeInfo, text: String): Boolean {
        if (!isSmartPunctuationEnabled(this)) return false

        val beforeTrailingSpace = text.dropLast(1)
        val punct = beforeTrailingSpace.lastOrNull() ?: return false
        val punctuationChars = parseSmartPunctuationChars(getSmartPunctuationCharsRaw(this))
        if (punct !in punctuationChars) return false

        val punctIndex = beforeTrailingSpace.length - 1
        var spaceStart = punctIndex
        while (spaceStart > 0 && beforeTrailingSpace[spaceStart - 1] == ' ') spaceStart--
        val spacesBeforePunct = punctIndex - spaceStart
        if (spacesBeforePunct == 0) return false

        val trigger = " ".repeat(spacesBeforePunct) + punct
        val expansion = "$punct "

        expandText(source, text, trigger, expansion)
        return true
    }

    private fun processTextForExpansion(source: AccessibilityNodeInfo, text: String) {
        // Extract the last word (before the space)
        val words = text.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return

        val lastWord = words.last()

        // Check if this word matches any trigger
        val matchingSnippet = snippetsCache.firstOrNull { snippet ->
            snippet.trigger.equals(lastWord, ignoreCase = true)
        } ?: return

        // Process dynamic placeholders
        val processedExpansion = SnippetProcessor.process(matchingSnippet.expansion)

        // Perform the expansion
        expandText(source, text, lastWord, processedExpansion)
    }

    private fun expandText(
        source: AccessibilityNodeInfo,
        currentText: String,
        trigger: String,
        expansion: String
    ) {
        try {
            // Remove the trigger word and the trailing space
            val textBeforeTrigger = currentText.dropLast(trigger.length + 1)
            val newText = textBeforeTrigger + expansion

            // Save expansion history for undo
            lastExpansion = ExpansionHistory(
                trigger = trigger,
                expansion = expansion,
                textBeforeTrigger = textBeforeTrigger
            )

            // Set the new text using ACTION_SET_TEXT
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }

            source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            // Move cursor to the end
            arguments.clear()
            arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                newText.length
            )
            arguments.putInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                newText.length
            )
            source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments)

        } catch (e: Exception) {
            // Silently handle errors
        }
    }

    override fun onInterrupt() {
        dismissSuggestions()
    }

    override fun onDestroy() {
        dismissSuggestions()
        super.onDestroy()
        serviceScope.cancel()
    }
}

enum class SuggestionMenuLayout { LIST, HORIZONTAL }
enum class SuggestionColorMode { AUTO, DARK, LIGHT }

/**
 * Pure decision logic for "delete right after an expansion reverts it back to the
 * trigger". Kept separate from the service so it can be unit tested without an
 * Android runtime.
 *
 * The state we compare against is the text the service inserted:
 * `textBeforeTrigger + expansion` (expansion already has its dynamic placeholders
 * resolved).
 */
internal object ExpansionUndo {

    /**
     * True when [currentText] looks like the user just deleted into the
     * freshly-inserted expansion (any number of characters), so it should be
     * reverted to the trigger.
     *
     * We deliberately do not require exactly one character to have been removed:
     * dynamic expansions contain punctuation and digits (e.g. "2026-08-26") and the
     * keyboard, which never saw the programmatic insert, often removes a whole chunk
     * on a single backspace.
     */
    fun shouldUndo(currentText: String, textBeforeTrigger: String, expansion: String): Boolean {
        val fullExpandedText = textBeforeTrigger + expansion
        return currentText.length < fullExpandedText.length &&
            currentText.startsWith(textBeforeTrigger) &&
            fullExpandedText.startsWith(currentText)
    }

    /**
     * True when the user has moved on from the expansion (typed more text, or edited
     * it into something that is no longer a prefix of what we inserted). The undo
     * history is stale at that point and should be discarded so it can't mis-fire
     * on an unrelated later edit.
     *
     * Note: the exact-match case (`currentText == fullExpandedText`) is treated as
     * "not stale" so the history survives our own ACTION_SET_TEXT echo event.
     */
    fun isHistoryStale(currentText: String, textBeforeTrigger: String, expansion: String): Boolean {
        val fullExpandedText = textBeforeTrigger + expansion
        return currentText != fullExpandedText && !fullExpandedText.startsWith(currentText)
    }
}
