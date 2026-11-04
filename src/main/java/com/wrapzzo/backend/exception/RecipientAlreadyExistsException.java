package com.wrapzzo.backend.exception;

public class RecipientAlreadyExistsException extends RuntimeException {

    public RecipientAlreadyExistsException(String name) {
        super("A recipient matching this name, phone, and email already exists: " + name);
    }

    private RecipientAlreadyExistsException(String message, boolean unused) {
        super(message);
    }

    public static RecipientAlreadyExistsException pcode(String pcode, String name) {
        return new RecipientAlreadyExistsException("A doctor with pcode " + pcode + " already exists: " + name, true);
    }
}
