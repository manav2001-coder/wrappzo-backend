package com.wrapzzo.backend.exception;

/** A template, or a mapping to one, that breaks a rule: a platform-only key in a company template, a loop, a foreign template. */
public class InvalidTemplateException extends RuntimeException {

    public InvalidTemplateException(String message) {
        super(message);
    }
}
