package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Who added and last changed a master row. {@link AuditNameListener} fills both from the signed-in
 * user, so every write path is covered without each service stamping it.
 */
@MappedSuperclass
@EntityListeners(AuditNameListener.class)
@Getter
@Setter
public abstract class AuditNames {

    @Column(name = "created_by_name", updatable = false)
    private String createdByName;

    @Column(name = "updated_by_name")
    private String updatedByName;
}
