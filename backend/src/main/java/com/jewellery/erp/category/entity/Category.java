package com.jewellery.erp.category.entity;

import com.jewellery.erp.common.entity.NamedMasterEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The kind of ornament: Ring, Chain, Necklace, Bangle, and so on. */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category extends NamedMasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Category category) || id == null) {
            return false;
        }
        return id.equals(category.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
