package com.wrapzzo.backend.model;

/**
 * PRD 2.7 -- Draft -> Submitted -> Approved, or Submitted -> Rejected -> Resubmitted ->
 * Approved. RESUBMITTED behaves exactly like SUBMITTED for review purposes -- it exists
 * only so a rejected-then-refiled submission is visibly distinct in history.
 */
public enum SubmissionStatus {
    DRAFT,
    SUBMITTED,
    REJECTED,
    RESUBMITTED,
    APPROVED
}
