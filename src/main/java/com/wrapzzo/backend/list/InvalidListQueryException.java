package com.wrapzzo.backend.list;

/** The list was asked for in a way that cannot be answered (an unknown field, a bad filter value). Answers 400 with the reason. */
public class InvalidListQueryException extends RuntimeException {

    public InvalidListQueryException(String message) {
        super(message);
    }
}
