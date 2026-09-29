package com.jewellery.erp.shop.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The shop this installation belongs to.
 *
 * <p>Exactly one row, id 1, enforced by a check constraint. Keeping it as a
 * table rather than configuration means the shop owner edits their own GSTIN and
 * address from the UI, and means a future multi-branch release can lift the
 * singleton constraint instead of reworking the model.
 */
@Entity
@Table(name = "shop_settings")
@Getter
@Setter
@NoArgsConstructor
public class ShopSettings extends AuditableEntity {

    /** The only id this table ever holds. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    private Long id = SINGLETON_ID;

    @Column(name = "shop_name", nullable = false, length = 150)
    private String shopName;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "alternate_mobile_number", length = 20)
    private String alternateMobileNumber;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "gstin", length = 15)
    private String gstin;
}
