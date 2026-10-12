package com.meridian.payments.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStatusClassificationStrategyTest {

    private DefaultStatusClassificationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DefaultStatusClassificationStrategy();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "COMPLETED", "DONE", "success", "completed", "done", " SUCCESS "})
    @DisplayName("Should classify success statuses correctly")
    void shouldClassifySuccessStatuses(String status) {
        ExecutionCategory category = strategy.classify(status, null);
        assertThat(category).isEqualTo(ExecutionCategory.SUCCESS);
        assertThat(strategy.isSuccess(status, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TIMEOUT", "NETWORK_ERROR", "TEMPORARY_FAILURE", "NETWORK_TIMEOUT", "timeout", "network_error"})
    @DisplayName("Should classify retriable failure statuses correctly")
    void shouldClassifyRetriableStatuses(String status) {
        ExecutionCategory category = strategy.classify(status, null);
        assertThat(category).isEqualTo(ExecutionCategory.RETRIABLE_FAILURE);
        assertThat(strategy.isRetriable(status, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_CONFIGURATION", "PERMISSION_DENIED", "REPORT_NOT_FOUND", "invalid_configuration", "permission_denied", "report_not_found"})
    @DisplayName("Should classify non-retriable failure statuses correctly")
    void shouldClassifyNonRetriableStatuses(String status) {
        ExecutionCategory category = strategy.classify(status, null);
        assertThat(category).isEqualTo(ExecutionCategory.NON_RETRIABLE_FAILURE);
        assertThat(strategy.isNonRetriable(status, null)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "FAILED, REPORT_NOT_FOUND, NON_RETRIABLE_FAILURE",
            "FAILED, INVALID_CONFIGURATION, NON_RETRIABLE_FAILURE",
            "FAILED, PERMISSION_DENIED, NON_RETRIABLE_FAILURE",
            "FAILED, NETWORK_TIMEOUT, RETRIABLE_FAILURE",
            "FAILED, TIMEOUT, RETRIABLE_FAILURE",
            "ERROR, TEMPORARY_FAILURE, RETRIABLE_FAILURE"
    })
    @DisplayName("Should perform composite classification using errorCode when status is generic FAILED")
    void shouldClassifyUsingErrorCodeWhenStatusIsGeneric(String status, String errorCode, ExecutionCategory expected) {
        ExecutionCategory category = strategy.classify(status, errorCode);
        assertThat(category).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN_ERROR", "SOME_RANDOM_CODE", "FATAL_CRASH", ""})
    @DisplayName("Should classify unrecognized statuses as UNKNOWN")
    void shouldClassifyUnrecognizedAsUnknown(String status) {
        ExecutionCategory category = strategy.classify(status, "CODE_99");
        assertThat(category).isEqualTo(ExecutionCategory.UNKNOWN);
    }

    @Test
    @DisplayName("Should handle null status and null error code gracefully")
    void shouldHandleNullInputsGracefully() {
        ExecutionCategory category = strategy.classify(null, null);
        assertThat(category).isEqualTo(ExecutionCategory.UNKNOWN);
    }
}
