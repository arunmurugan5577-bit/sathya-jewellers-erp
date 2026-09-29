package com.jewellery.erp.itemtype.entity;

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

/**
 * The material an item is made of: Gold, Silver, Platinum, Diamond.
 *
 * <p>Purities hang off an item type, which is what lets the purity list in the
 * inventory form follow the selected material without any hardcoded mapping.
 */
@Entity
@Table(name = "item_types")
@Getter
@Setter
@NoArgsConstructor
public class ItemType extends NamedMasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemType itemType) || id == null) {
            return false;
        }
        return id.equals(itemType.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
