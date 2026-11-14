package com.classicchess.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One game of a new study plan, in playback order, with an optional plain-text note on what to look for. */
data class PlanGame(val game: String, val note: String? = null)

/**
 * The website preferences to change; each null field keeps its saved value. Keys such as [boardTheme] come from
 * the catalogs accountPreferences returns. When the server refuses any value, nothing changes.
 */
data class PreferenceChanges(
    val disableAnimations: Boolean? = null,
    val readingFont: String? = null,
    val boardTheme: String? = null,
    val pieceSet: String? = null,
    val boardInputMode: String? = null,
    val keyboardMoveEntry: Boolean? = null,
    val moveTreeTheme: String? = null,
    /** Board drawing colors such as "#15781B", without and with the Ctrl, Alt and Ctrl+Alt modifiers. */
    val annotationColorNormal: String? = null,
    val annotationColorCtrl: String? = null,
    val annotationColorAlt: String? = null,
    val annotationColorCtrlAlt: String? = null,
    val autoplaySeconds: Int? = null,
    val engineDepth: Int? = null,
    val engineLines: Int? = null,
    val engineEvalBar: Boolean? = null,
    val engineEnabled: Boolean? = null,
    /** Choosing a sound set turns website narration off. */
    val soundSet: String? = null,
    val soundVolume: Int? = null,
    /** Narration needs speech_available; turning it on selects the silent sound set. */
    val speechEnabled: Boolean? = null,
    val speechLevel: Double? = null,
    val speechVoice: String? = null,
    /** The complete viewer layout state as accountPreferences returns it; it replaces the saved one. */
    val viewerLayouts: JsonObject? = null,
    /** Keys for each game-viewer command; a command left out returns to its default keys. */
    val viewerKeybindings: Map<String, List<String>>? = null,
) {
    internal fun toJson(): JsonObject = buildJsonObject {
        disableAnimations?.let { put("disable_animations", it) }
        readingFont?.let { put("reading_font", it) }
        boardTheme?.let { put("board_theme", it) }
        pieceSet?.let { put("piece_set", it) }
        boardInputMode?.let { put("board_input_mode", it) }
        keyboardMoveEntry?.let { put("keyboard_move_entry", it) }
        moveTreeTheme?.let { put("move_tree_theme", it) }
        annotationColorNormal?.let { put("annotation_color_normal", it) }
        annotationColorCtrl?.let { put("annotation_color_ctrl", it) }
        annotationColorAlt?.let { put("annotation_color_alt", it) }
        annotationColorCtrlAlt?.let { put("annotation_color_ctrl_alt", it) }
        autoplaySeconds?.let { put("autoplay_seconds", it) }
        engineDepth?.let { put("engine_depth", it) }
        engineLines?.let { put("engine_lines", it) }
        engineEvalBar?.let { put("engine_eval_bar", it) }
        engineEnabled?.let { put("engine_enabled", it) }
        soundSet?.let { put("sound_set", it) }
        soundVolume?.let { put("sound_volume", it) }
        speechEnabled?.let { put("speech_enabled", it) }
        speechLevel?.let { put("speech_level", it) }
        speechVoice?.let { put("speech_voice", it) }
        viewerLayouts?.let { put("viewer_layouts", it) }
        viewerKeybindings?.let { bindings ->
            put("viewer_keybindings", buildJsonObject { bindings.forEach { (command, keys) -> strings(command, keys) } })
        }
    }
}

/**
 * The Android app's settings to change for a device session; each null field keeps its value. Keys come from
 * mobileSettings. Sound and speech here are the app's own, separate from the website's preferences.
 */
data class MobileSettingsChanges(
    val boardTheme: String? = null,
    val pieceSet: String? = null,
    val engineDepth: Int? = null,
    val engineLines: Int? = null,
    val engineEvalBar: Boolean? = null,
    val engineEnabled: Boolean? = null,
    /** Choosing a sound set turns speech off. */
    val soundSet: String? = null,
    val soundVolume: Int? = null,
    val speechEnabled: Boolean? = null,
    val speechLevel: Double? = null,
    val speechVoice: String? = null,
    /** The Cast coach address; an empty string removes it. */
    val castCoachEmail: String? = null,
    val castCcEmails: List<String>? = null,
    val castBccEmails: List<String>? = null,
) {
    internal fun toJson(): JsonObject = buildJsonObject {
        boardTheme?.let { put("board_theme", it) }
        pieceSet?.let { put("piece_set", it) }
        engineDepth?.let { put("engine_depth", it) }
        engineLines?.let { put("engine_lines", it) }
        engineEvalBar?.let { put("engine_eval_bar", it) }
        engineEnabled?.let { put("engine_enabled", it) }
        soundSet?.let { put("sound_set", it) }
        soundVolume?.let { put("sound_volume", it) }
        speechEnabled?.let { put("speech_enabled", it) }
        speechLevel?.let { put("speech_level", it) }
        speechVoice?.let { put("speech_voice", it) }
        castCoachEmail?.let { put("cast_coach_email", it) }
        castCcEmails?.let { strings("cast_cc_emails", it) }
        castBccEmails?.let { strings("cast_bcc_emails", it) }
    }
}

internal fun JsonObjectBuilder.strings(key: String, values: List<String>) {
    put(key, JsonArray(values.map(::JsonPrimitive)))
}
