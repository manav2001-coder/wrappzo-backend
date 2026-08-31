package com.wrapzzo.backend.exception;

public class RecipientAlreadyExistsException extends RuntimeException {

    public RecipientAlreadyExistsException(String name) {
        super("A recipient matching this name, phone, and email already exists: " + name);
    }
}
