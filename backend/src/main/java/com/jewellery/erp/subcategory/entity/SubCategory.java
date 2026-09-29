package com.jewellery.erp.subcategory.entity;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.common.entity.NamedMasterEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A subdivision of a category - "Men's Ring" under "Ring".
 *
 * <p>The parent is mandatory: a sub category without a category has no meaning,
 * and the foreign key says so at the database level as well as here.
 */
@Entity
@Table(name = "sub_categories")
@Getter
@Setter
@NoArgsConstructor
public class SubCategory extends NamedMasterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubCategory subCategory) || id == null) {
            return false;
        }
        return id.equals(subCategory.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
