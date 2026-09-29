package com.jewellery.erp.hsn.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A Harmonised System of Nomenclature code and the GST rate that applies to it.
 *
 * <p>{@code gstPercentage} is {@link BigDecimal} backed by {@code NUMERIC(5,2)}.
 * A tax rate multiplied by a floating point value produces answers that do not
 * reconcile, and a GST return has to reconcile exactly.
 */
@Entity
@Table(name = "hsn_codes")
@Getter
@Setter
@NoArgsConstructor
public class HsnCode extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hsn_code", nullable = false, length = 8)
    private String hsnCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "gst_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercentage;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HsnCode hsn) || id == null) {
            return false;
        }
        return id.equals(hsn.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
