package com.jewellery.erp.labels.service;

import com.jewellery.erp.purity.entity.Purity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Turns a purity master row into the line printed under a serial number:
 * "22K / 916" for gold, "925" for silver.
 *
 * <p>The purity master already holds the shop's own wording in {@code name}
 * ("22K / 916", "925", "950"), so that is what a label shows - nothing here
 * invents a vocabulary the masters screen does not use. The karat is worked out
 * only when a name is missing, and only for gold, where karat is meaningful:
 * silver and platinum are quoted in fineness alone.
 */
@Component
public class PurityLabelFormatter {

    private static final String GOLD_CODE = "GOLD";
    private static final BigDecimal PURE = BigDecimal.valueOf(999.9);
    private static final BigDecimal KARAT_SCALE = BigDecimal.valueOf(24);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    /**
     * The purity as a label prints it, or {@code null} when it does not belong on
     * one.
     *
     * <p>Only gold carries its purity on the tag: karat is what a customer asks
     * about, and the shop's own silver tags carry no fineness at all. Everything
     * else shows its name, weight and size and leaves it at that.
     */
    public String labelText(Purity purity) {
        if (purity == null || purity.getItemType() == null) {
            return null;
        }
        String code = purity.getItemType().getCode();
        return code != null && GOLD_CODE.equalsIgnoreCase(code.trim()) ? format(purity) : null;
    }

    /**
     * @return the text for the purity line, or {@code null} when the piece has no
     *     purity to print - the caller reports that as a validation failure
     *     rather than printing a label with a gap in it
     */
    public String format(Purity purity) {
        if (purity == null) {
            return null;
        }
        String name = purity.getName() == null ? "" : purity.getName().trim();
        if (!name.isEmpty()) {
            return name;
        }
        BigDecimal value = purity.getPurityValue();
        if (value == null) {
            return null;
        }
        String fineness = trim(value);
        String code = purity.getItemType() == null || purity.getItemType().getCode() == null
                ? ""
                : purity.getItemType().getCode().toUpperCase();
        if (!GOLD_CODE.equals(code)) {
            return fineness;
        }
        return "%sK / %s".formatted(trim(karat(value)), fineness);
    }

    /** 916 -> 22, 999 -> 24. Rounded, because 916/1000 x 24 is 21.98. */
    private static BigDecimal karat(BigDecimal fineness) {
        BigDecimal capped = fineness.min(PURE);
        return capped.multiply(KARAT_SCALE).divide(THOUSAND, 0, RoundingMode.HALF_UP);
    }

    /** 916.000 -> "916", 99.500 -> "99.5": the stored scale is not the printed one. */
    private static String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
