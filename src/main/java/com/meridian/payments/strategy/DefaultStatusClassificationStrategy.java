package com.meridian.payments.strategy;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Default implementation of StatusClassificationStrategy that handles composite
 * evaluation of runner status strings and error codes with case-insensitivity.
 */
@Component
public class DefaultStatusClassificationStrategy implements StatusClassificationStrategy {

    private static final Set<String> SUCCESS_SET = Set.of(
            "SUCCESS",
            "COMPLETED",
            "DONE"
    );

    private static final Set<String> RETRIABLE_SET = Set.of(
            "TIMEOUT",
            "NETWORK_ERROR",
            "TEMPORARY_FAILURE",
            "NETWORK_TIMEOUT"
    );

    private static final Set<String> NON_RETRIABLE_SET = Set.of(
            "INVALID_CONFIGURATION",
            "PERMISSION_DENIED",
            "REPORT_NOT_FOUND"
    );

    @Override
    public ExecutionCategory classify(String status, String errorCode) {
        String normalizedStatus = normalize(status);
        String normalizedError = normalize(errorCode);

        // 1. Direct check on status
        if (SUCCESS_SET.contains(normalizedStatus)) {
            return ExecutionCategory.SUCCESS;
        }
        if (RETRIABLE_SET.contains(normalizedStatus)) {
            return ExecutionCategory.RETRIABLE_FAILURE;
        }
        if (NON_RETRIABLE_SET.contains(normalizedStatus)) {
            return ExecutionCategory.NON_RETRIABLE_FAILURE;
        }

        // 2. Composite check on errorCode (common when status is "FAILED" or "ERROR")
        if (RETRIABLE_SET.contains(normalizedError)) {
            return ExecutionCategory.RETRIABLE_FAILURE;
        }
        if (NON_RETRIABLE_SET.contains(normalizedError)) {
            return ExecutionCategory.NON_RETRIABLE_FAILURE;
        }
        if (SUCCESS_SET.contains(normalizedError)) {
            return ExecutionCategory.SUCCESS;
        }

        // 3. Substring matching for compound error codes (e.g., ERR_TIMEOUT, NETWORK_FAIL)
        if (normalizedStatus.contains("TIMEOUT") || normalizedError.contains("TIMEOUT")
                || normalizedStatus.contains("NETWORK") || normalizedError.contains("NETWORK")) {
            return ExecutionCategory.RETRIABLE_FAILURE;
        }

        return ExecutionCategory.UNKNOWN;
    }

    @Override
    public boolean isSuccess(String status, String errorCode) {
        return classify(status, errorCode) == ExecutionCategory.SUCCESS;
    }

    @Override
    public boolean isRetriable(String status, String errorCode) {
        return classify(status, errorCode) == ExecutionCategory.RETRIABLE_FAILURE;
    }

    @Override
    public boolean isNonRetriable(String status, String errorCode) {
        return classify(status, errorCode) == ExecutionCategory.NON_RETRIABLE_FAILURE;
    }

    @Override
    public Set<String> getRecognizedSuccessStatuses() {
        return Collections.unmodifiableSet(SUCCESS_SET);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
