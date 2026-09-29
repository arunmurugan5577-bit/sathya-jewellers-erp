package com.jewellery.erp.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * The columns every "name + code" master shares: item types, categories and sub
 * categories.
 *
 * <p>Purity and HSN deliberately do not extend this - their identity is a
 * numeric value and a statutory code respectively, and forcing them into this
 * shape would mean carrying a column that means nothing to them.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class NamedMasterEntity extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Stored upper case; unique within the master. */
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Soft-delete flag. Master data is deactivated rather than removed so that
     * historical inventory keeps pointing at a readable record.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
