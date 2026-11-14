package ru.antispam.app.privacy.remediation

import android.content.Context
import android.content.Intent
import ru.antispam.app.privacy.model.SecurityIssue
import ru.antispam.app.privacy.model.SecurityIssueSeverity
import ru.antispam.app.privacy.model.SecurityIssueType

/**
 * Types of remediation actions supported by the One-Click Remediation Engine.
 */
enum class RemediationActionType {
    // Tier 1: Instant In-App Auto-Fix (zero user clicks required)
    INTERNAL_SYNC_REGISTRY,
    INTERNAL_ENABLE_MIC_GUARD,
    INTERNAL_ENABLE_CLIPBOARD_GUARD,
    INTERNAL_COPY_VPN_CONFIG,

    // Tier 2: Guided Interactive Wizard (deep link + onResume verification)
    SYSTEM_REVOKE_NOTIFICATION_LISTENER,
    SYSTEM_MANAGE_ACCESSIBILITY,
    SYSTEM_REMOVE_ROOT_CA,
    SYSTEM_RESET_CALL_FORWARDING,
    SYSTEM_DISABLE_2G,
    SYSTEM_RESTRICT_LOCATION,

    // Advisory
    ADVISORY_ROOT_OR_HOOKS
}

/**
 * Represents an individual actionable step in the remediation plan.
 */
data class RemediationStep(
    val id: String,
    val issueType: SecurityIssueType,
    val actionType: RemediationActionType,
    val title: String,
    val description: String,
    val instruction: String,
    val actionLabel: String,
    val severity: SecurityIssueSeverity,
    val scoreImpact: Int,
    val isAutoFixable: Boolean,
    val targetPackage: String? = null,
    val targetAppName: String? = null,
    val intent: Intent? = null,
    val canBeWhitelisted: Boolean = false,
    val isCompleted: Boolean = false
)

/**
 * High-level plan containing separated automatic in-app tasks and guided user steps.
 */
data class RemediationPlan(
    val totalIssuesCount: Int,
    val automatedSteps: List<RemediationStep>,
    val guidedSteps: List<RemediationStep>,
    val projectedScore: Int
) {
    val totalStepsCount: Int
        get() = automatedSteps.size + guidedSteps.size

    val hasGuidedSteps: Boolean
        get() = guidedSteps.isNotEmpty()
}

/**
 * Live state of the One-Click Remediation execution.
 */
sealed class RemediationExecutionState {
    object Idle : RemediationExecutionState()
    
    data class AutoFixing(
        val currentStepTitle: String,
        val progress: Int,
        val total: Int
    ) : RemediationExecutionState()

    data class GuidedStepPending(
        val step: RemediationStep,
        val stepIndex: Int,
        val totalSteps: Int,
        val currentScore: Int
    ) : RemediationExecutionState()

    data class Completed(
        val finalScore: Int,
        val fixedIssuesCount: Int
    ) : RemediationExecutionState()
}
