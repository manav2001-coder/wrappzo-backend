package com.wrapzzo.backend.exception;

public class RecipientPhotoRequestNotPossibleException extends RuntimeException {

    public RecipientPhotoRequestNotPossibleException(String reason) {
        super(reason);
    }
}
