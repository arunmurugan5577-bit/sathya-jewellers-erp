package com.jewellery.erp.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.sales.dto.SaleRequests;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule that keeps the two kinds of invoice line apart.
 *
 * <p>A tagged piece is named by its serial and the server reads the rest off
 * the stock record. A loose piece - the small gold the shop sells without ever
 * tagging it - has no serial and no stock record, so everything a tag would
 * have said is typed instead. A line that tried to be both would leave two
 * answers to the same question.
 */
@DisplayName("A sale line is a tagged piece or a loose one")
class LooseSaleLineTest {

    @Test
    @DisplayName("a serial is enough on its own")
    void aSerialIsEnoughOnItsOwn() {
        assertThatCode(() -> SaleService.assertLineIsOneKindOrTheOther(tagged("000123")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a serial with hand-typed masters is refused, and says why")
    void aSerialWithMastersIsRefused() {
        SaleRequests.Item line = new SaleRequests.Item(
                "000123", null, 1L, 2L, 3L, null, 4L, null, null, null, new BigDecimal("9000"), null);

        assertThatThrownBy(() -> SaleService.assertLineIsOneKindOrTheOther(line))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("000123")
                .hasMessageContaining("already says what the piece is");
    }

    @Test
    @DisplayName("a loose line names every field it is still missing")
    void aLooseLineNamesWhatIsMissing() {
        SaleRequests.Item bare = new SaleRequests.Item(
                null, null, null, null, null, null, null, null, null, null, new BigDecimal("9000"), null);

        assertThatThrownBy(() -> SaleService.assertLineIsOneKindOrTheOther(bare))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(thrown -> assertThat(((BusinessRuleException) thrown).getFieldErrors())
                        .containsOnlyKeys(
                                "particulars", "itemTypeId", "purityId", "categoryId", "hsnId", "weightGrams"));
    }

    @Test
    @DisplayName("a loose line that says all of it is accepted")
    void aFullyDescribedLooseLineIsAccepted() {
        assertThatCode(() -> SaleService.assertLineIsOneKindOrTheOther(loose())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a sub category is optional - not every piece has one")
    void theSubCategoryIsOptional() {
        assertThat(loose().subCategoryId()).isNull();
        assertThatCode(() -> SaleService.assertLineIsOneKindOrTheOther(loose())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a blank serial is a loose line, not a tagged one with an empty tag")
    void aBlankSerialIsLoose() {
        SaleRequests.Item blank = new SaleRequests.Item(
                "   ", null, null, null, null, null, null, null, null, null, new BigDecimal("9000"), null);

        // Loose, so it is told what a loose line needs - not let through as a
        // tagged line whose serial would then be looked up and not found.
        assertThatThrownBy(() -> SaleService.assertLineIsOneKindOrTheOther(blank))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sold without a serial number");
    }

    private static SaleRequests.Item tagged(String serial) {
        return new SaleRequests.Item(
                serial, null, null, null, null, null, null, null, null, null, new BigDecimal("9000"), null);
    }

    /** A mookuthi: 0.42 g of 22K gold, described by hand. */
    private static SaleRequests.Item loose() {
        return new SaleRequests.Item(
                null,
                "AZ12B4",
                1L,
                3L,
                6L,
                null,
                1L,
                "Mookuthi",
                new BigDecimal("0.420"),
                null,
                new BigDecimal("9000"),
                new BigDecimal("150"));
    }
}
