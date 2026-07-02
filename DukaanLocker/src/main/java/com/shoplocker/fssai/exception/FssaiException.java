package com.shoplocker.fssai.exception;

public class FssaiException extends RuntimeException {

    public FssaiException(String message) {
        super(message);
    }

    public FssaiException(String message, Throwable cause) {
        super(message, cause);
    }
}
