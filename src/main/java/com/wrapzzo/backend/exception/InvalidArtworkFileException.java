package com.wrapzzo.backend.exception;

/** An artwork upload that is not a PDF, or is not the file this order's presign handed out. Answers 400. */
public class InvalidArtworkFileException extends RuntimeException {

    public InvalidArtworkFileException(String message) {
        super(message);
    }
}
