package com.wrapzzo.backend.service;

/** A scheduled job the Background jobs screen can show, and run again when a run failed. */
public interface BackgroundJob {

    /** Stable id of the job, kept on every run record. */
    String name();

    /** Short human name, e.g. "Refresh token cleanup". */
    String label();

    /** Does the work; returns a one-line summary of what it did. */
    String run();
}
