package com.group9.asaa.classes.communication.model

/**
 * Terminal failure reasons could be expanded.
 */
sealed class FailureReason(val reason: String) {
    data object EnrichmentFailed: FailureReason("enrichment_failed")
}
