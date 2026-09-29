package com.jewellery.erp.labels;

import static org.assertj.core.api.Assertions.assertThat;

import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.labels.service.PurityLabelFormatter;
import com.jewellery.erp.purity.entity.Purity;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurityLabelFormatterTest {

    private final PurityLabelFormatter formatter = new PurityLabelFormatter();

    private static Purity purity(String code, String name, String value) {
        ItemType itemType = new ItemType();
        itemType.setCode(code);
        itemType.setName(code);
        Purity purity = new Purity();
        purity.setItemType(itemType);
        purity.setName(name);
        purity.setPurityValue(value == null ? null : new BigDecimal(value));
        return purity;
    }

    @Test
    @DisplayName("the purity master's own wording is what the label prints")
    void prefersTheMasterName() {
        assertThat(formatter.format(purity("GOLD", "22K / 916", "916.000"))).isEqualTo("22K / 916");
        assertThat(formatter.format(purity("GOLD", "24K / 999", "999.000"))).isEqualTo("24K / 999");
        assertThat(formatter.format(purity("SILV", "925", "925.000"))).isEqualTo("925");
        assertThat(formatter.format(purity("PLAT", "950", "950.000"))).isEqualTo("950");
    }

    @Test
    @DisplayName("without a name, gold gets its karat worked out and other metals show fineness alone")
    void derivesFromFineness() {
        assertThat(formatter.format(purity("GOLD", null, "916.000"))).isEqualTo("22K / 916");
        assertThat(formatter.format(purity("GOLD", "  ", "999.000"))).isEqualTo("24K / 999");
        assertThat(formatter.format(purity("GOLD", null, "750.000"))).isEqualTo("18K / 750");
        assertThat(formatter.format(purity("SILV", null, "999.000"))).isEqualTo("999");
        assertThat(formatter.format(purity("PLAT", null, "950.000"))).isEqualTo("950");
    }

    @Test
    @DisplayName("a label prints purity for gold only - silver and platinum tags carry none")
    void labelTextIsGoldOnly() {
        assertThat(formatter.labelText(purity("GOLD", "22K / 916", "916.000"))).isEqualTo("22K / 916");
        assertThat(formatter.labelText(purity("gold", null, "999.000"))).isEqualTo("24K / 999");
        assertThat(formatter.labelText(purity("SILV", "925", "925.000"))).isNull();
        assertThat(formatter.labelText(purity("PLAT", "950", "950.000"))).isNull();
        assertThat(formatter.labelText(null)).isNull();
    }

    @Test
    void reportsNothingToPrintRatherThanPrintingAGap() {
        assertThat(formatter.format(null)).isNull();
        assertThat(formatter.format(purity("GOLD", null, null))).isNull();
    }
}
