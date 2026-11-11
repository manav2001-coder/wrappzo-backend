package com.wrapzzo.backend.exception;

import java.util.UUID;

public class ImportJobNotFoundException extends RuntimeException {

    public ImportJobNotFoundException(UUID id) {
        super("No import " + id + " was found for you -- it may have been cleared (finished imports are kept for a week)");
    }
}
