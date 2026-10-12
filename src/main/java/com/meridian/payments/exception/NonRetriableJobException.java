package com.meridian.payments.exception;

public class NonRetriableJobException extends RuntimeException {
    public NonRetriableJobException(String message) {
        super(message);
    }
}
