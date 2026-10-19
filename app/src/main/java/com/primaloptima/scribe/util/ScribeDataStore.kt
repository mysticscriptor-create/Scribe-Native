package com.primaloptima.scribe.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.primaloptima.scribe.util.model.ShortcutAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.primaloptima.scribe.util.export.*

/**
 * Phase 2-A: Single DataStore for the entire app.
 * Replaces both PrefsManager (SharedPreferences) and ThemeDataStoreRepo.
 * One file = one source of truth per concern. All writes are suspend, all reads are Flow.
 *
 * DataStore is safe to use from coroutines — reads/writes are transactional
 * and guaranteed not to block the main thread.
 */

// Single DataStore file — only one preferencesDataStore per name per process.
// Using applicationContext as the receiver prevents Activity-context leaks.
private val Context.scribeDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "scribe_prefs")

class ScribeDataStore(private val context: Context) {

    private val store = context.applicationContext.scribeDataStore

    // ─────────────────────────────────────────────────────────────────────────
    // Keys — all in one place. Adding a setting = one line here only.
    // Key names intentionally match the old PrefsManager constants for smooth
    // migration (DataStore will not find old SharedPreferences values — users
    // start fresh for DataStore-backed keys, which is acceptable since
    // preferences like theme choice are easily re-applied).
    // ─────────────────────────────────────────────────────────────────────────

    companion object Keys {
        // Identity
        val ACTIVE_NOTE_ID     = stringPreferencesKey("active_note_id")
        val VAULT_NAME         = stringPreferencesKey("vault_name")
        val EXTERNAL_ROOT_JSON = stringPreferencesKey("external_root_json")

        // Theme
        val ACTIVE_THEME_ID    = stringPreferencesKey("active_theme_id")
        val CUSTOM_THEMES_JSON = stringPreferencesKey("custom_themes_json")
        val LEGACY_BLUR_ENABLED= booleanPreferencesKey("legacy_blur_enabled")

        // Per-theme background images (dynamic keys — one per theme)
        fun bgUriKey(themeId: String)     = stringPreferencesKey("bg_uri_$themeId")
        fun bgOpacityKey(themeId: String) = floatPreferencesKey("bg_opacity_$themeId")

        // Layout & UI
        val GRID_COLUMNS       = intPreferencesKey("grid_columns")
        val HOME_START_PAGE    = stringPreferencesKey("home_start_page")
        val VIEW_MODE          = stringPreferencesKey("view_mode")
        val ONGOING_PROJECT_ID = stringPreferencesKey("ongoing_project_id")

        // Editor
        val SHOW_WORD_COUNT    = booleanPreferencesKey("show_word_count")
        val TYPEWRITER_MODE    = booleanPreferencesKey("typewriter_mode")
        val RESTORE_CURSOR_ON_OPEN = booleanPreferencesKey("restore_cursor_on_open")
        val LINE_SPACING       = stringPreferencesKey("line_spacing")
        val EDITOR_FONT_SIZE   = intPreferencesKey("editor_font_size")
        val MANUSCRIPT_ORNAMENT_ID = stringPreferencesKey("manuscript_ornament_id")
        val LAST_KEYBOARD_HEIGHT   = floatPreferencesKey("last_keyboard_height")

        // Writing stats
        val DAILY_GOAL         = intPreferencesKey("daily_goal")

        // Shortcuts & Pinned
        val SHORTCUTS_JSON     = stringPreferencesKey("shortcuts_json")
        val DISABLED_CATEGORIES_JSON = stringPreferencesKey("disabled_categories_json")
        val CUSTOM_CATEGORIES_JSON = stringPreferencesKey("custom_categories_json")
        val LAST_SNIPPET_CAT   = stringPreferencesKey("last_snippet_cat")
        val LAST_TEMPLATE_CAT  = stringPreferencesKey("last_template_cat")
        val PINNED_JSON        = stringPreferencesKey("pinned_json")

        // Companion panel — pinned notes slots (persisted as JSON list of note IDs)
        val PINNED_TOP_JSON    = stringPreferencesKey("pinned_top_json")
        val PINNED_BOTTOM_JSON = stringPreferencesKey("pinned_bottom_json")
        // Companion panel — UI prefs
        val COMPANION_TAB_BAR_BOTTOM   = booleanPreferencesKey("companion_tab_bar_bottom")
        val COMPANION_SPLIT_HORIZONTAL = booleanPreferencesKey("companion_split_horizontal") // true = side-by-side, false = up/down
        val WORKBENCH_STATE_JSON       = stringPreferencesKey("workbench_state_json")

        // Per-book goals (dynamic)
        fun bookGoalKey(bookId: String) = stringPreferencesKey("book_goal_$bookId")

        // History settings
        val AUTO_HISTORY_ENABLED       = booleanPreferencesKey("auto_history_enabled")
        val MANUAL_CHECKPOINTS_ENABLED = booleanPreferencesKey("manual_checkpoints_enabled")
        val AUTO_HISTORY_SLOTS         = intPreferencesKey("auto_history_slots")
        val MANUAL_CHECKPOINT_SLOTS    = intPreferencesKey("manual_checkpoint_slots")
        val AUTO_HISTORY_MIN_WORDS     = intPreferencesKey("auto_history_min_words")

        // One-time backfill flag — set after runWordCountBackfill() completes
        // so it never re-runs on subsequent launches.
        val WORD_COUNT_BACKFILL_DONE   = booleanPreferencesKey("word_count_backfill_done")

        // Export preferences (Increment 6)
        val EXPORT_LAST_FORMAT         = stringPreferencesKey("export_last_format")
        val EXPORT_PAPER_SIZE          = stringPreferencesKey("export_paper_size")
        val EXPORT_MARGIN_PRESET       = stringPreferencesKey("export_margin_preset")
        val EXPORT_FONT_CHOICE         = stringPreferencesKey("export_font_choice")
        val EXPORT_FONT_SIZE_PT        = intPreferencesKey("export_font_size_pt")
        val EXPORT_LINE_SPACING        = floatPreferencesKey("export_line_spacing")
        val EXPORT_INCLUDE_COVER_TITLE = booleanPreferencesKey("export_include_cover_title")
        val EXPORT_INCLUDE_TOC         = booleanPreferencesKey("export_include_toc")
        val EXPORT_IMAGE_QUALITY       = stringPreferencesKey("export_image_quality")
        val EXPORT_AUTHOR_PEN_NAME     = stringPreferencesKey("export_author_pen_name")
        val EXPORT_ORNAMENT_ID         = stringPreferencesKey("export_ornament_id")
        val EXPORT_DROP_CAP_STYLE      = stringPreferencesKey("export_drop_cap_style")
        val EXPORT_DROP_CAP_LINES      = intPreferencesKey("export_drop_cap_lines")
        val EXPORT_FIRST_LINE_INDENT   = booleanPreferencesKey("export_first_line_indent")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Flows — reactive UI reads. Collect these in ViewModels via stateIn().
    // ─────────────────────────────────────────────────────────────────────────

    val exportConfigFlow: Flow<ExportConfig> = store.data.map { prefs ->
        val fmtStr = prefs[EXPORT_LAST_FORMAT] ?: "pdf"
        val format = ExportFormat.fromId(fmtStr)

        val paperStr = prefs[EXPORT_PAPER_SIZE] ?: "A4"
        val paper = runCatching { PaperSize.valueOf(paperStr) }.getOrDefault(PaperSize.A4)

        val marginStr = prefs[EXPORT_MARGIN_PRESET] ?: "STANDARD"
        val margin = runCatching { MarginPreset.valueOf(marginStr) }.getOrDefault(MarginPreset.STANDARD)

        val fontStr = prefs[EXPORT_FONT_CHOICE] ?: "CLASSIC_SERIF"
        val font = runCatching { FontChoice.valueOf(fontStr) }.getOrDefault(FontChoice.CLASSIC_SERIF)

        val fontSize = prefs[EXPORT_FONT_SIZE_PT] ?: 11
        val lineSpacing = prefs[EXPORT_LINE_SPACING] ?: 1.6f
        val includeCover = prefs[EXPORT_INCLUDE_COVER_TITLE] ?: true
        val includeToc = prefs[EXPORT_INCLUDE_TOC] ?: true

        val qualityStr = prefs[EXPORT_IMAGE_QUALITY] ?: "HIGH_RES"
        val quality = runCatching { ImageQuality.valueOf(qualityStr) }.getOrDefault(ImageQuality.HIGH_RES)

        val author = prefs[EXPORT_AUTHOR_PEN_NAME] ?: ""
        val ornament = prefs[EXPORT_ORNAMENT_ID] ?: (prefs[MANUSCRIPT_ORNAMENT_ID] ?: "classic_diamond")
        val dropCapStyleStr = prefs[EXPORT_DROP_CAP_STYLE] ?: "DISABLED"
        val dropCapStyle = runCatching { com.primaloptima.scribe.util.export.DropCapStyle.valueOf(dropCapStyleStr) }
            .getOrDefault(com.primaloptima.scribe.util.export.DropCapStyle.DISABLED)
        val dropCapLines = prefs[EXPORT_DROP_CAP_LINES] ?: 3
        val firstLineIndent = prefs[EXPORT_FIRST_LINE_INDENT] ?: false

        ExportConfig(
            format = format,
            paperSize = paper,
            marginPreset = margin,
            fontChoice = font,
            fontSizePt = fontSize.coerceIn(8, 24),
            lineSpacingMultiplier = lineSpacing.coerceIn(1.0f, 2.4f),
            includeCoverTitle = includeCover,
            includeTableOfContents = includeToc,
            imageQuality = quality,
            authorName = author,
            ornamentId = ornament,
            dropCapStyle = dropCapStyle,
            dropCapLines = dropCapLines.coerceIn(2, 4),
            firstLineIndent = firstLineIndent
        )
    }

    val activeNoteIdFlow: Flow<String?>      = store.data.map { it[ACTIVE_NOTE_ID] }
    val vaultNameFlow: Flow<String>          = store.data.map { it[VAULT_NAME] ?: "My Vault" }
    val externalRootJsonFlow: Flow<String?>  = store.data.map { it[EXTERNAL_ROOT_JSON] }

    val activeThemeIdFlow: Flow<String>      = store.data.map { it[ACTIVE_THEME_ID] ?: "paper" }
    val customThemesJsonFlow: Flow<String>   = store.data.map { it[CUSTOM_THEMES_JSON] ?: "[]" }
    val legacyBlurEnabledFlow: Flow<Boolean> = store.data.map { it[LEGACY_BLUR_ENABLED] ?: false }

    val gridColumnsFlow: Flow<Int>           = store.data.map { it[GRID_COLUMNS] ?: 2 }
    val homeStartPageFlow: Flow<String>      = store.data.map { it[HOME_START_PAGE] ?: "books" }
    val viewModeFlow: Flow<String>           = store.data.map { it[VIEW_MODE] ?: "tree" }
    val ongoingProjectIdFlow: Flow<String?>  = store.data.map { it[ONGOING_PROJECT_ID] }

    val showWordCountFlow: Flow<Boolean>     = store.data.map { it[SHOW_WORD_COUNT] ?: true }
    val typewriterModeFlow: Flow<Boolean>    = store.data.map { it[TYPEWRITER_MODE] ?: false }
    val restoreCursorOnOpenFlow: Flow<Boolean> = store.data.map { it[RESTORE_CURSOR_ON_OPEN] ?: false }
    val lineSpacingFlow: Flow<String>        = store.data.map { it[LINE_SPACING] ?: "comfortable" }
    val editorFontSizeFlow: Flow<Int>        = store.data.map { it[EDITOR_FONT_SIZE] ?: 16 }
    val manuscriptOrnamentIdFlow: Flow<String> = store.data.map { it[MANUSCRIPT_ORNAMENT_ID] ?: "classic_diamond" }
    val lastKeyboardHeightFlow: Flow<Float>    = store.data.map { it[LAST_KEYBOARD_HEIGHT] ?: 270f }

    val dailyGoalFlow: Flow<Int>             = store.data.map { it[DAILY_GOAL] ?: 500 }
    val shortcutsJsonFlow: Flow<String?>     = store.data.map { it[SHORTCUTS_JSON] }
    val pinnedJsonFlow: Flow<String?>        = store.data.map { it[PINNED_JSON] }

    // Companion panel persistent state
    val pinnedTopJsonFlow: Flow<String?>     = store.data.map { it[PINNED_TOP_JSON] }
    val pinnedBottomJsonFlow: Flow<String?>  = store.data.map { it[PINNED_BOTTOM_JSON] }
    val companionTabBarBottomFlow: Flow<Boolean>   = store.data.map { it[COMPANION_TAB_BAR_BOTTOM] ?: false }
    val companionSplitHorizontalFlow: Flow<Boolean> = store.data.map { it[COMPANION_SPLIT_HORIZONTAL] ?: false }
    val workbenchStateJsonFlow: Flow<String?> = store.data.map { it[WORKBENCH_STATE_JSON] }

    val autoHistoryEnabledFlow: Flow<Boolean>       = store.data.map { it[AUTO_HISTORY_ENABLED] ?: true }
    val manualCheckpointsEnabledFlow: Flow<Boolean> = store.data.map { it[MANUAL_CHECKPOINTS_ENABLED] ?: true }
    val autoHistorySlotsFlow: Flow<Int>             = store.data.map { it[AUTO_HISTORY_SLOTS] ?: 10 }
    val manualCheckpointSlotsFlow: Flow<Int>        = store.data.map { it[MANUAL_CHECKPOINT_SLOTS] ?: 10 }
    val autoHistoryMinWordsFlow: Flow<Int>          = store.data.map { it[AUTO_HISTORY_MIN_WORDS] ?: 10 }

    fun bgUriFlow(themeId: String): Flow<String?>   = store.data.map { it[bgUriKey(themeId)] }
    fun bgOpacityFlow(themeId: String): Flow<Float> = store.data.map { it[bgOpacityKey(themeId)] ?: 0.35f }
    /** Reactive Flow variant of [getBookGoal] — use in ViewModels that need live updates. */
    fun bookGoalFlow(bookId: String): Flow<BookGoal> = store.data.map { prefs ->
        val json = prefs[bookGoalKey(bookId)] ?: return@map BookGoal()
        try { AppJson.decodeFromString<BookGoal>(json) }
        catch (_: Exception) { BookGoal() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Synchronous reads (suspend) — for boot-time and non-Flow callers only.
    // Always prefer the Flow variants in UI/ViewModel code.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getActiveThemeId(): String =
        store.data.first()[ACTIVE_THEME_ID] ?: "paper"

    suspend fun getCustomThemesJson(): String =
        store.data.first()[CUSTOM_THEMES_JSON] ?: "[]"

    suspend fun getAutoHistoryEnabled(): Boolean =
        store.data.first()[AUTO_HISTORY_ENABLED] ?: true

    suspend fun getAutoHistoryMinWords(): Int =
        store.data.first()[AUTO_HISTORY_MIN_WORDS] ?: 10

    suspend fun getAutoHistorySlots(): Int =
        store.data.first()[AUTO_HISTORY_SLOTS] ?: 10

    suspend fun getManualCheckpointsEnabled(): Boolean =
        store.data.first()[MANUAL_CHECKPOINTS_ENABLED] ?: true

    suspend fun getManualCheckpointSlots(): Int =
        store.data.first()[MANUAL_CHECKPOINT_SLOTS] ?: 10

    suspend fun getExternalRootJson(): String? =
        store.data.first()[EXTERNAL_ROOT_JSON]

    suspend fun getShortcuts(): List<ShortcutAction> {
        val allDefaults = DefaultShortcuts.all + DefaultShortcuts.defaultSnippets + DefaultShortcuts.defaultTemplates
        val json = store.data.first()[SHORTCUTS_JSON] ?: return allDefaults
        return try {
            val list = AppJson.decodeFromString<List<ShortcutAction>>(json)
            val defaultMap = allDefaults.associateBy { it.id }

            // Migration step 1: Migrate legacy category IDs and fill missing keywords
            val migratedList = list.map { action ->
                val def = defaultMap[action.id]
                var cat = action.category
                if (action.id in setOf("paren", "bracket", "brace", "sym_status_bracket", "sym_lenticular", "sym_white_square", "sym_angle_bracket", "sym_double_angle", "sym_floor")) {
                    cat = DefaultShortcuts.CAT_BRACKETS
                } else if (action.id in setOf("hr", "sym_asterism", "sym_three_stars", "sym_sparkle", "sym_black_star", "sym_section", "sym_fleuron")) {
                    cat = DefaultShortcuts.CAT_SCENE_BREAKS
                } else if (action.id == "ellipsis") {
                    cat = DefaultShortcuts.CAT_PUNCTUATION
                }

                val resolvedIcon = when {
                    action.icon.isNotBlank() -> action.icon
                    def != null && action.payload == def.payload && action.closing == def.closing -> def.icon
                    else -> DefaultShortcuts.autoDetectIcon(action.kind, action.payload, action.closing)
                }
                val resolvedLabel = when {
                    def != null && (action.label.isBlank() || action.label.trim() == def.icon.trim()) -> def.label
                    else -> action.label
                }

                action.copy(
                    label = resolvedLabel,
                    icon = resolvedIcon,
                    category = cat,
                    keywords = if (action.keywords.isEmpty() && def != null) def.keywords else action.keywords
                )
            }

            // Migration step 2: Merge any newly available default shortcuts, snippets, and templates
            val allDefaults = DefaultShortcuts.all + DefaultShortcuts.defaultSnippets + DefaultShortcuts.defaultTemplates
            val existingIds = migratedList.map { it.id }.toSet()
            val missingDefaults = allDefaults.filter { it.id !in existingIds }
            if (missingDefaults.isNotEmpty()) {
                migratedList + missingDefaults
            } else {
                migratedList
            }
        } catch (_: Exception) { DefaultShortcuts.all + DefaultShortcuts.defaultSnippets + DefaultShortcuts.defaultTemplates }
    }

    suspend fun getDisabledCategories(): Set<String> {
        val json = store.data.first()[DISABLED_CATEGORIES_JSON] ?: return emptySet()
        return try {
            AppJson.decodeFromString<Set<String>>(json)
        } catch (_: Exception) { emptySet() }
    }

    suspend fun setDisabledCategoriesJson(json: String) {
        store.edit { it[DISABLED_CATEGORIES_JSON] = json }
    }

    suspend fun getCustomCategories(): Map<String, List<String>> {
        val json = store.data.first()[CUSTOM_CATEGORIES_JSON] ?: return emptyMap()
        return try {
            AppJson.decodeFromString<Map<String, List<String>>>(json)
        } catch (_: Exception) { emptyMap() }
    }

    suspend fun setCustomCategoriesJson(json: String) {
        store.edit { it[CUSTOM_CATEGORIES_JSON] = json }
    }

    suspend fun getLastSnippetCategory(): String =
        store.data.first()[LAST_SNIPPET_CAT] ?: "all"

    suspend fun getLastTemplateCategory(): String =
        store.data.first()[LAST_TEMPLATE_CAT] ?: "all"

    suspend fun setLastSnippetCategory(cat: String) = store.edit {
        it[LAST_SNIPPET_CAT] = cat
    }

    suspend fun setLastTemplateCategory(cat: String) = store.edit {
        it[LAST_TEMPLATE_CAT] = cat
    }

    suspend fun getBookGoal(bookId: String): BookGoal {
        val json = store.data.first()[bookGoalKey(bookId)] ?: return BookGoal()
        return try { AppJson.decodeFromString<BookGoal>(json) }
        catch (_: Exception) { BookGoal() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Writes — all suspend, all execute off the main thread via DataStore internals.
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun setActiveNoteId(id: String?) = store.edit { prefs ->
        if (id == null) prefs.remove(ACTIVE_NOTE_ID) else prefs[ACTIVE_NOTE_ID] = id
    }

    suspend fun setVaultName(name: String) = store.edit { it[VAULT_NAME] = name }

    suspend fun setExternalRootJson(json: String?) = store.edit { prefs ->
        if (json == null) prefs.remove(EXTERNAL_ROOT_JSON) else prefs[EXTERNAL_ROOT_JSON] = json
    }

    suspend fun setActiveThemeId(id: String) = store.edit { it[ACTIVE_THEME_ID] = id }

    suspend fun setCustomThemesJson(json: String) = store.edit { it[CUSTOM_THEMES_JSON] = json }

    suspend fun setLegacyBlurEnabled(v: Boolean) = store.edit { it[LEGACY_BLUR_ENABLED] = v }

    suspend fun setGridColumns(cols: Int) = store.edit { it[GRID_COLUMNS] = cols }

    suspend fun setHomeStartPage(page: String) = store.edit { it[HOME_START_PAGE] = page }

    suspend fun setViewMode(mode: String) = store.edit { it[VIEW_MODE] = mode }

    suspend fun setOngoingProjectId(id: String?) = store.edit { prefs ->
        if (id == null) prefs.remove(ONGOING_PROJECT_ID) else prefs[ONGOING_PROJECT_ID] = id
    }

    suspend fun setShowWordCount(v: Boolean) = store.edit { it[SHOW_WORD_COUNT] = v }

    suspend fun setTypewriterMode(v: Boolean) = store.edit { it[TYPEWRITER_MODE] = v }
    suspend fun setRestoreCursorOnOpen(v: Boolean) = store.edit { it[RESTORE_CURSOR_ON_OPEN] = v }

    suspend fun setLineSpacing(v: String) = store.edit { it[LINE_SPACING] = v }

    suspend fun setEditorFontSize(size: Int) = store.edit { it[EDITOR_FONT_SIZE] = size.coerceIn(12, 28) }
    suspend fun setLastKeyboardHeight(heightDp: Float) = store.edit { it[LAST_KEYBOARD_HEIGHT] = heightDp }

    suspend fun setDailyGoal(goal: Int) = store.edit { it[DAILY_GOAL] = maxOf(50, goal) }

    // ── Word count backfill flag ───────────────────────────────────────────────

    /** Returns true if the one-time word-count backfill has already completed. */
    suspend fun isWordCountBackfillDone(): Boolean =
        store.data.first()[WORD_COUNT_BACKFILL_DONE] ?: false

    /** Marks the word-count backfill as done so it never re-runs on subsequent launches. */
    suspend fun markWordCountBackfillDone() =
        store.edit { it[WORD_COUNT_BACKFILL_DONE] = true }

    suspend fun setShortcutsJson(json: String?) = store.edit { prefs ->
        if (json == null) prefs.remove(SHORTCUTS_JSON) else prefs[SHORTCUTS_JSON] = json
    }

    suspend fun setPinnedJson(json: String) = store.edit { it[PINNED_JSON] = json }
    suspend fun setManuscriptOrnamentId(id: String) = store.edit { it[MANUSCRIPT_ORNAMENT_ID] = id }

    // Companion panel write functions
    suspend fun setPinnedTopJson(json: String)    = store.edit { it[PINNED_TOP_JSON]    = json }
    suspend fun setPinnedBottomJson(json: String) = store.edit { it[PINNED_BOTTOM_JSON] = json }
    suspend fun setCompanionTabBarBottom(v: Boolean) = store.edit { it[COMPANION_TAB_BAR_BOTTOM]   = v }
    suspend fun setCompanionSplitHorizontal(v: Boolean) = store.edit { it[COMPANION_SPLIT_HORIZONTAL] = v }
    suspend fun setWorkbenchStateJson(json: String) = store.edit { it[WORKBENCH_STATE_JSON] = json }

    suspend fun saveBookGoal(bookId: String, goal: BookGoal) = store.edit {
        it[bookGoalKey(bookId)] = AppJson.encodeToString(goal)
    }

    suspend fun setAutoHistoryEnabled(v: Boolean) = store.edit { it[AUTO_HISTORY_ENABLED] = v }

    suspend fun setManualCheckpointsEnabled(v: Boolean) = store.edit { it[MANUAL_CHECKPOINTS_ENABLED] = v }

    suspend fun setAutoHistorySlots(v: Int) = store.edit { it[AUTO_HISTORY_SLOTS] = v.coerceIn(1, 30) }

    suspend fun setManualCheckpointSlots(v: Int) = store.edit { it[MANUAL_CHECKPOINT_SLOTS] = v.coerceIn(1, 30) }

    suspend fun setAutoHistoryMinWords(v: Int) = store.edit { it[AUTO_HISTORY_MIN_WORDS] = v.coerceIn(1, 200) }

    suspend fun setThemeBackground(themeId: String, uri: String?, opacity: Float) = store.edit { prefs ->
        if (uri != null) prefs[bgUriKey(themeId)] = uri else prefs.remove(bgUriKey(themeId))
        prefs[bgOpacityKey(themeId)] = opacity
    }

    // ── Export preferences write functions (Increment 6) ──────────────────────

    suspend fun saveExportConfig(config: ExportConfig) = store.edit { prefs ->
        prefs[EXPORT_LAST_FORMAT] = config.format.id
        prefs[EXPORT_PAPER_SIZE] = config.paperSize.name
        prefs[EXPORT_MARGIN_PRESET] = config.marginPreset.name
        prefs[EXPORT_FONT_CHOICE] = config.fontChoice.name
        prefs[EXPORT_FONT_SIZE_PT] = config.fontSizePt
        prefs[EXPORT_LINE_SPACING] = config.lineSpacingMultiplier
        prefs[EXPORT_INCLUDE_COVER_TITLE] = config.includeCoverTitle
        prefs[EXPORT_INCLUDE_TOC] = config.includeTableOfContents
        prefs[EXPORT_IMAGE_QUALITY] = config.imageQuality.name
        prefs[EXPORT_AUTHOR_PEN_NAME] = config.authorName
        prefs[EXPORT_ORNAMENT_ID] = config.ornamentId
        prefs[EXPORT_DROP_CAP_STYLE] = config.dropCapStyle.name
        prefs[EXPORT_DROP_CAP_LINES] = config.dropCapLines
        prefs[EXPORT_FIRST_LINE_INDENT] = config.firstLineIndent
    }

    suspend fun resetExportConfig() = store.edit { prefs ->
        prefs.remove(EXPORT_LAST_FORMAT)
        prefs.remove(EXPORT_PAPER_SIZE)
        prefs.remove(EXPORT_MARGIN_PRESET)
        prefs.remove(EXPORT_FONT_CHOICE)
        prefs.remove(EXPORT_FONT_SIZE_PT)
        prefs.remove(EXPORT_LINE_SPACING)
        prefs.remove(EXPORT_INCLUDE_COVER_TITLE)
        prefs.remove(EXPORT_INCLUDE_TOC)
        prefs.remove(EXPORT_IMAGE_QUALITY)
        prefs.remove(EXPORT_AUTHOR_PEN_NAME)
        prefs.remove(EXPORT_ORNAMENT_ID)
        prefs.remove(EXPORT_DROP_CAP_STYLE)
        prefs.remove(EXPORT_DROP_CAP_LINES)
        prefs.remove(EXPORT_FIRST_LINE_INDENT)
    }
}

// ── BookGoal (moved here from PrefsManager inner class) ──────────────────────
// Kept as a top-level data class so both ScribeDataStore and callers can use it.
@Serializable
data class BookGoal(
    val dailyWords: Int = 500,
    val totalTarget: Int = 120_000,
    val chapterTarget: Int = 0  // 0 = not set
)
