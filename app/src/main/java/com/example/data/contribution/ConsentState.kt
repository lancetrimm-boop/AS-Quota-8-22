package com.example.data.contribution

/**
 * Strongly typed consent state model for Global Aura Intelligence contributions (Phase 3B.2).
 *
 * - [NOT_DECIDED]: Default state for a fresh installation. Fails closed (no queueing, no transmission).
 * - [GRANTED]: Explicit user opt-in. Sanitized contribution payloads can be queued and processed.
 * - [REVOKED]: Explicit user opt-out. Contributions cannot be queued, and existing queued outbound records are immediately purged.
 */
enum class ConsentState {
    NOT_DECIDED,
    GRANTED,
    REVOKED
}
