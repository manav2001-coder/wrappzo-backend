package com.wrapzzo.backend.exception;

public class PhotoOrEmailRequiredException extends RuntimeException {

    public PhotoOrEmailRequiredException() {
        super("This gift requires a recipient photo -- upload one now, or provide the recipient's email so we can send them an upload link.");
    }
}
