package com.wrapzzo.backend.exception;

public class GiftNameAlreadyExistsException extends RuntimeException {

    public GiftNameAlreadyExistsException(String name) {
        super("A gift with this name already exists: " + name);
    }
}
