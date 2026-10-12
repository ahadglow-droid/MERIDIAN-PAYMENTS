package com.meridian.payments.strategy;

import java.util.Set;

/**
 * Strategy interface for categorizing heterogeneous execution status strings and error codes.
 */
public interface StatusClassificationStrategy {

    ExecutionCategory classify(String status, String errorCode);

    boolean isSuccess(String status, String errorCode);

    boolean isRetriable(String status, String errorCode);

    boolean isNonRetriable(String status, String errorCode);

    Set<String> getRecognizedSuccessStatuses();
}
