package com.jewellery.erp.purity.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.itemtype.entity.ItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A fineness value belonging to one item type - 22K / 916 for gold, 925 for
 * silver.
 *
 * <p>Modelled as a master rather than an enum so a shop can add a purity it
 * trades in without a code change, and so the inventory form can offer exactly
 * the purities that make sense for the chosen material.
 *
 * <p>{@code purityValue} is parts per thousand in {@code NUMERIC(6,3)}: it feeds
 * pure-weight and rate calculations, where floating point error compounds into
 * money.
 */
@Entity
@Table(name = "purities")
@Getter
@Setter
@NoArgsConstructor
public class Purity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_type_id", nullable = false)
    private ItemType itemType;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "purity_value", nullable = false, precision = 6, scale = 3)
    private BigDecimal purityValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Purity purity) || id == null) {
            return false;
        }
        return id.equals(purity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
