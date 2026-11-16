package com.wrapzzo.backend.exception;

public class WrongCurrentPasswordException extends RuntimeException {

    public WrongCurrentPasswordException() {
        super("Your current password is not correct");
    }
}
