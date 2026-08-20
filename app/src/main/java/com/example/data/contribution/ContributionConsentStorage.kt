package com.example.data.contribution

import android.content.Context
import android.content.SharedPreferences

/**
 * Storage abstraction for persisting [ConsentState] (Phase 3B.2).
 */
interface ContributionConsentStorage {
    fun getConsentState(): ConsentState
    fun setConsentState(state: ConsentState)
}

/**
 * Persistent implementation of [ContributionConsentStorage] backed by Android [SharedPreferences].
 *
 * FAIL-CLOSED SAFETY GUARANTEES:
 * - If preferences cannot be accessed, returns [ConsentState.NOT_DECIDED].
 * - If stored value is corrupted, unreadable, or invalid, returns [ConsentState.NOT_DECIDED].
 * - NEVER defaults to [ConsentState.GRANTED].
 */
class SharedPreferencesConsentStorage(
    context: Context,
    prefsName: String = "aura_contribution_consent_prefs"
) : ContributionConsentStorage {

    private val prefs: SharedPreferences? = try {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null
    }

    override fun getConsentState(): ConsentState {
        val raw = try {
            prefs?.getString(KEY_CONSENT_STATE, null)
        } catch (e: Exception) {
            null
        } ?: return ConsentState.NOT_DECIDED

        return try {
            ConsentState.valueOf(raw)
        } catch (e: Exception) {
            // Fail closed on invalid or corrupted enum names
            ConsentState.NOT_DECIDED
        }
    }

    override fun setConsentState(state: ConsentState) {
        try {
            prefs?.edit()?.putString(KEY_CONSENT_STATE, state.name)?.apply()
        } catch (e: Exception) {
            // Write errors fail silently; in-memory state will remain active
        }
    }

    companion object {
        private const val KEY_CONSENT_STATE = "global_intelligence_consent_state"
    }
}

/**
 * In-memory implementation of [ContributionConsentStorage] for unit testing and process restart simulations.
 */
class InMemoryConsentStorage(
    private var persistedState: ConsentState = ConsentState.NOT_DECIDED
) : ContributionConsentStorage {

    override fun getConsentState(): ConsentState = persistedState

    override fun setConsentState(state: ConsentState) {
        persistedState = state
    }
}
