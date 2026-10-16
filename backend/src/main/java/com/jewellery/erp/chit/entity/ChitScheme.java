package com.jewellery.erp.chit.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One of the monthly amounts the shop offers: 500, 1,000, 2,000, 3,000, 5,000
 * or 10,000 a month.
 *
 * <p>A scheme, not a customer's chit. Changing the amount here changes what a
 * new joiner is offered and leaves every open chit alone, because a chit
 * copies its terms at joining - see {@link ChitAccount}.
 */
@Entity
@Table(name = "chit_schemes")
@Getter
@Setter
@NoArgsConstructor
public class ChitScheme extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme_type", nullable = false, length = 10)
    private ChitSchemeType schemeType;

    @Column(name = "monthly_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyAmount;

    /** What the customer pays: eleven. */
    @Column(name = "payable_instalments", nullable = false)
    private short payableInstalments;

    /** What the chit is worth: twelve. The shop puts in the difference. */
    @Column(name = "total_instalments", nullable = false)
    private short totalInstalments;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** The months the shop puts in - the bonus, in instalments rather than rupees. */
    public short bonusInstalments() {
        return (short) (totalInstalments - payableInstalments);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ChitScheme scheme && id != null && id.equals(scheme.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ChitScheme(" + code + ")";
    }
}
