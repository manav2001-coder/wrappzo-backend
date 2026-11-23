package com.wrapzzo.backend.exception;

/** Thrown when a heavy screen could not get its turn in time: the server is working on a lot at once. Answered as 503 -- try again shortly. */
public class ServerBusyException extends RuntimeException {

    public ServerBusyException(String message) {
        super(message);
    }
}
