package com.wrapzzo.backend.exception;

public class GiftNameAlreadyExistsException extends RuntimeException {

    public GiftNameAlreadyExistsException(String name) {
        super("A PEL with this name already exists: " + name);
    }
}
