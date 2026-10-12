package com.meridian.payments.strategy;

/**
 * High-level classification category for report job execution outcomes.
 */
public enum ExecutionCategory {
    SUCCESS,
    RETRIABLE_FAILURE,
    NON_RETRIABLE_FAILURE,
    UNKNOWN
}
