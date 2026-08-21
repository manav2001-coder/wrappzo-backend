package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.AccessEffect;

/** Projection: one user access-control override's key and effect, without loading full entities. */
public interface UserAccessKeyEffect {

    String getKey();

    AccessEffect getEffect();
}
