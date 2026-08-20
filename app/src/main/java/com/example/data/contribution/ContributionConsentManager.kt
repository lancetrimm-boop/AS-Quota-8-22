package com.example.data.contribution

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authoritative thread-safe manager for Global Aura Intelligence contribution consent (Phase 3B.2).
 *
 * PRIVACY GUARANTEES:
 * 1. Default state is [ConsentState.NOT_DECIDED] (Fail-closed).
 * 2. Only returns [isConsentGranted] = true when explicitly set to [ConsentState.GRANTED].
 * 3. Storage errors, missing keys, or corrupted values fail closed.
 * 4. Transitioning to [ConsentState.REVOKED] or [ConsentState.NOT_DECIDED] immediately invokes
 *    [onConsentRevoked] to purge any outbound contribution queue records locally.
 * 5. Does NOT store or process user IDs, device IDs, advertising IDs, or IP addresses.
 */
class ContributionConsentManager(
    private val storage: ContributionConsentStorage = InMemoryConsentStorage(),
    private var onConsentRevoked: (() -> Unit)? = null
) {
    private val _consentStateFlow = MutableStateFlow(loadStateSafely())
    val consentStateFlow: StateFlow<ConsentState> = _consentStateFlow.asStateFlow()

    @Volatile
    private var currentState: ConsentState = _consentStateFlow.value

    private fun loadStateSafely(): ConsentState {
        return try {
            storage.getConsentState()
        } catch (e: Exception) {
            ConsentState.NOT_DECIDED
        }
    }

    /**
     * Registers listener invoked when consent is revoked or reset.
     */
    fun setOnConsentRevokedListener(listener: () -> Unit) {
        this.onConsentRevoked = listener
    }

    /**
     * Returns the current thread-safe [ConsentState].
     */
    @Synchronized
    fun getConsentState(): ConsentState {
        return currentState
    }

    /**
     * Updates the consent state and persists it to storage.
     * Triggers queue purging if consent is revoked or reset from GRANTED.
     */
    @Synchronized
    fun setConsentState(state: ConsentState) {
        val previousState = currentState
        currentState = state
        _consentStateFlow.value = state
        try {
            storage.setConsentState(state)
        } catch (e: Exception) {
            // Storage write error: memory state updated, state remains fail-safe
        }

        if (previousState == ConsentState.GRANTED && state != ConsentState.GRANTED) {
            onConsentRevoked?.invoke()
        } else if (state == ConsentState.REVOKED || state == ConsentState.NOT_DECIDED) {
            onConsentRevoked?.invoke()
        }
    }

    /**
     * Convenience method to grant consent ([ConsentState.GRANTED]).
     */
    fun grantConsent() {
        setConsentState(ConsentState.GRANTED)
    }

    /**
     * Convenience method to revoke consent ([ConsentState.REVOKED]).
     * Triggers immediate purging of pending outbound contribution queue.
     */
    fun revokeConsent() {
        setConsentState(ConsentState.REVOKED)
    }

    /**
     * Convenience method to reset consent to [ConsentState.NOT_DECIDED].
     */
    fun resetConsent() {
        setConsentState(ConsentState.NOT_DECIDED)
    }

    /**
     * Returns true ONLY if consent is explicitly [ConsentState.GRANTED].
     * Fails closed for [ConsentState.NOT_DECIDED], [ConsentState.REVOKED], or storage errors.
     */
    fun isConsentGranted(): Boolean {
        return currentState == ConsentState.GRANTED
    }

    /**
     * Forces re-reading consent state from storage (e.g. following process restart).
     */
    @Synchronized
    fun reloadFromStorage(): ConsentState {
        currentState = loadStateSafely()
        _consentStateFlow.value = currentState
        return currentState
    }
}
