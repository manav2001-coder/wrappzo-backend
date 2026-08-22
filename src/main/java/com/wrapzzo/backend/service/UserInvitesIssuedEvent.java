package com.wrapzzo.backend.service;

import java.util.List;

/** Published once the invite tokens are saved; the actual emails go out after commit, off the request thread. */
public record UserInvitesIssuedEvent(List<PendingInvite> invites) {
}
