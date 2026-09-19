package com.tripmate.common.exception;

/** Resource gone (e.g. expired invite link) -> HTTP 410. */
public class GoneException extends RuntimeException {
    public GoneException(String message) {
        super(message);
    }
}
