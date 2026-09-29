package com.jewellery.erp.customer.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer of the shop.
 *
 * <p>Shared by sales and old gold/silver purchases. Documents copy the name and
 * address at the moment they are issued, so editing a customer never alters an
 * invoice that has already been handed over.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_code", nullable = false, length = 20, updatable = false)
    private String customerCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 6)
    private String pincode;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Single-line address as printed on a bill: "Thangamma Colony, Udumalpet - 642126". */
    public String formattedAddress() {
        String cityAndPin = Stream.of(city, pincode)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" - "));
        String joined = Stream.of(addressLine1, addressLine2, cityAndPin)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? null : joined;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Customer customer) || id == null) {
            return false;
        }
        return id.equals(customer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
