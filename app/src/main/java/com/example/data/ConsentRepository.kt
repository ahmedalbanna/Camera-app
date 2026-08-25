package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aladasa_prefs")

/**
 * Phase 3: Consent & AI preferences — opt-in, persisted via DataStore.
 * All AI off by default; Gemini only when consented + has valid key + Wi-Fi.
 */
class ConsentRepository(private val context: Context) {

    companion object {
        private val KEY_AI_CONSENT = booleanPreferencesKey("ai_consent")
        private val KEY_GEMINI_ENABLED = booleanPreferencesKey("gemini_enabled")
        private val KEY_SMART_ORGANIZE = booleanPreferencesKey("smart_organize")
        private val KEY_CAPTURE_ASSIST = booleanPreferencesKey("capture_assist")
    }

    val aiConsentFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AI_CONSENT] ?: false }
    val geminiEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_GEMINI_ENABLED] ?: false }
    val smartOrganizeFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SMART_ORGANIZE] ?: false }
    val captureAssistFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_CAPTURE_ASSIST] ?: true }

    suspend fun setAiConsent(consented: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AI_CONSENT] = consented
            if (!consented) {
                prefs[KEY_GEMINI_ENABLED] = false
                prefs[KEY_SMART_ORGANIZE] = false
            }
        }
    }

    suspend fun setGeminiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GEMINI_ENABLED] = enabled }
    }

    suspend fun setSmartOrganize(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SMART_ORGANIZE] = enabled }
    }

    suspend fun setCaptureAssist(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CAPTURE_ASSIST] = enabled }
    }
}
