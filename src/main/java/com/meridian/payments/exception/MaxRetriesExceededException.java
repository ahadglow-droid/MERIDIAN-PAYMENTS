package com.meridian.payments.exception;

public class MaxRetriesExceededException extends RuntimeException {
    public MaxRetriesExceededException(String message) {
        super(message);
    }
}
