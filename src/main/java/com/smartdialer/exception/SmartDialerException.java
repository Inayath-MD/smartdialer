package com.smartdialer.exception;

public class SmartDialerException extends RuntimeException {
    public SmartDialerException(String message) {
        super(message);
    }

    public SmartDialerException(String message, Throwable cause) {
        super(message, cause);
    }
}
