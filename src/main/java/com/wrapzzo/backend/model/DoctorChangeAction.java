package com.wrapzzo.backend.model;

/** What an MR is proposing for a doctor -- ADD is a brand-new recipient, EDIT changes an existing one's details, REMOVE only unlinks the requester's own assignment. */
public enum DoctorChangeAction {
    ADD,
    EDIT,
    REMOVE
}
