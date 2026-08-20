package com.example.data

/**
 * Formal lifecycle states for the Aura Intelligence Center.
 */
enum class IntelligenceLifecycleState {
    FINDING_DETECTED,
    SYSTEM_ANALYSIS,
    SUGGESTED_IMPROVEMENT,
    NEEDS_REVIEW,
    APPROVED,
    REJECTED,
    NEEDS_MORE_INFORMATION,
    IMPLEMENTATION_PLANNED,
    IMPLEMENTATION_IN_PROGRESS,
    IMPLEMENTATION_COMPLETE,
    IMPLEMENTATION_FAILED,
    DEVIATION_DETECTED,
    VERIFICATION_IN_PROGRESS,
    VERIFICATION_PASSED,
    VERIFICATION_FAILED,
    MONITORING,
    VALIDATED,
    INCONCLUSIVE,
    REGRESSION_DETECTED,
    ROLLBACK_RECOMMENDED,
    ROLLED_BACK
}

/**
 * Classification for intelligence findings.
 */
enum class FindingClassification {
    ACTION_REQUIRED,
    IMPROVEMENT_OPPORTUNITY,
    INFORMATIONAL,
    NO_ACTION_REQUIRED,
    INSUFFICIENT_EVIDENCE,
    REGRESSION
}

/**
 * Plain-English confidence levels for intelligence assessments.
 */
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Type of action taken within the intelligence lifecycle.
 */
enum class IntelligenceActionType {
    IMPLEMENTATION,
    VERIFICATION,
    ROLLBACK
}

/**
 * Status of a specific intelligence action.
 */
enum class IntelligenceActionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Tracks the user's review status of an intelligence item.
 * Independent of the authoritative IntelligenceLifecycleState.
 */
enum class ReviewStatus {
    UNREAD,
    SEEN,
    REVIEWED,
    ACTION_TAKEN
}
