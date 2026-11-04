package com.wrapzzo.backend.exception;

public class PcodeRequiredException extends RuntimeException {

    public PcodeRequiredException() {
        super("A doctor's pcode is required.");
    }
}
