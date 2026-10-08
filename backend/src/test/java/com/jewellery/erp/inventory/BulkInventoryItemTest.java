package com.jewellery.erp.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.StateConflictException;
import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.inventory.entity.InventoryStatus;
import com.jewellery.erp.inventory.service.InventoryItemService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A box of metti, sold by the gram.
 *
 * <p>The shop's box weighs 92.500 g and holds something like 120 toe rings. A
 * customer takes four; the shop weighs what they took - 4.100 g - and bills
 * that. The box stays on the shelf, and on sale, until the last gram of it goes.
 *
 * <p>Two things are being pinned down here. First, that a box's weight and its
 * status can never disagree: SOLD means empty and empty means SOLD, which is the
 * invariant the sales module reads back and the database check constraints
 * enforce underneath. Second, that an ordinary single article still behaves
 * exactly as it did - all there, or gone.
 */
class BulkInventoryItemTest {

    /** Nothing in these methods touches a repository, so no mocks are needed. */
    private final InventoryItemService service =
            new InventoryItemService(null, null, null, null, null, null, null, null, null);

    private static InventoryItem box(String grams) {
        InventoryItem item = new InventoryItem();
        item.setSerialNumber("284");
        item.setBulk(true);
        item.setWeightGrams(new BigDecimal(grams));
        item.setRemainingWeightGrams(new BigDecimal(grams));
        return item;
    }

    private static InventoryItem ring(String grams) {
        InventoryItem item = new InventoryItem();
        item.setSerialNumber("285");
        item.setBulk(false);
        item.setWeightGrams(new BigDecimal(grams));
        item.setRemainingWeightGrams(new BigDecimal(grams));
        return item;
    }

    @Nested
    @DisplayName("Selling out of a box")
    class SellingFromABox {

        @Test
        @DisplayName("four toe rings off a 92.500 g box leaves 88.400 g, and the box is still on sale")
        void partialSaleLeavesTheBoxOnSale() {
            InventoryItem metti = box("92.500");

            metti.billOut(new BigDecimal("4.100"));

            assertThat(metti.getRemainingWeightGrams()).isEqualByComparingTo("88.400");
            assertThat(metti.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
            assertThat(metti.isSellable()).isTrue();
            assertThat(metti.isPartlySold()).isTrue();
        }

        @Test
        @DisplayName("sale after sale draws the box down to nothing, and only then is it SOLD")
        void theBoxIsSoldOnlyWhenEmpty() {
            InventoryItem metti = box("10.000");

            metti.billOut(new BigDecimal("4.000"));
            assertThat(metti.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);

            metti.billOut(new BigDecimal("5.999"));
            assertThat(metti.getRemainingWeightGrams()).isEqualByComparingTo("0.001");
            assertThat(metti.getStatus())
                    .as("a milligram is still stock")
                    .isEqualTo(InventoryStatus.AVAILABLE);

            metti.billOut(new BigDecimal("0.001"));
            assertThat(metti.getRemainingWeightGrams()).isEqualByComparingTo("0");
            assertThat(metti.getStatus()).isEqualTo(InventoryStatus.SOLD);
            assertThat(metti.isSellable()).isFalse();
        }

        @Test
        @DisplayName("more than is left is refused, and the message says how much there is")
        void refusesToOversell() {
            InventoryItem metti = box("92.500");
            metti.billOut(new BigDecimal("90.000"));

            assertThatThrownBy(() -> service.assertBillable(metti, new BigDecimal("5.000")))
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining("2.500")
                    .hasMessageContaining("284")
                    .extracting(error -> ((StateConflictException) error).getCode())
                    .isEqualTo(ErrorCode.INVENTORY_WEIGHT_EXCEEDED);
        }

        @Test
        @DisplayName("exactly what is left is allowed - the last of the box can be sold")
        void allowsTheLastOfTheBox() {
            InventoryItem metti = box("92.500");
            metti.billOut(new BigDecimal("90.000"));

            assertThat(service.assertBillable(metti, new BigDecimal("2.500")))
                    .isEqualByComparingTo("2.500");
        }

        @Test
        @DisplayName("a box with no weight entered is refused - it cannot be sold whole by accident")
        void refusesABoxWithNoWeight() {
            assertThatThrownBy(() -> service.assertBillable(box("92.500"), null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("sold by weight");
        }

        @Test
        @DisplayName("zero is refused")
        void refusesZero() {
            assertThatThrownBy(() -> service.assertBillable(box("92.500"), new BigDecimal("0.000")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("more than zero");
        }

        @Test
        @DisplayName("the weight is taken to the milligram, as the scale reads it")
        void roundsToTheMilligram() {
            assertThat(service.assertBillable(box("92.500"), new BigDecimal("4.10049")))
                    .isEqualByComparingTo("4.100");
        }
    }

    @Nested
    @DisplayName("An ordinary single article")
    class SingleArticle {

        @Test
        @DisplayName("is sold whole, and the counter does not get to name a weight")
        void isSoldWhole() {
            InventoryItem band = ring("5.250");

            assertThat(service.assertBillable(band, null)).isEqualByComparingTo("5.250");

            band.billOut(new BigDecimal("5.250"));
            assertThat(band.getStatus()).isEqualTo(InventoryStatus.SOLD);
            assertThat(band.getRemainingWeightGrams()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("refuses a weight rather than ignoring it")
        void refusesAWeight() {
            // Ignoring it silently would let the counter believe it had sold 2 g
            // of a 5.250 g ring, when the customer is being charged for all of it.
            assertThatThrownBy(() -> service.assertBillable(ring("5.250"), new BigDecimal("2.000")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("sold whole");
        }

        @Test
        @DisplayName("is never part sold")
        void isNeverPartSold() {
            InventoryItem band = ring("5.250");
            assertThat(band.isPartlySold()).isFalse();

            band.billOut(new BigDecimal("5.250"));
            assertThat(band.isPartlySold()).isTrue();
        }
    }

    @Nested
    @DisplayName("Cancelling the invoice")
    class Cancellation {

        @Test
        @DisplayName("gives the box back exactly what it gave")
        void returnsWhatWasTaken() {
            InventoryItem metti = box("92.500");
            metti.billOut(new BigDecimal("4.100"));

            metti.returnToStock(new BigDecimal("4.100"));

            assertThat(metti.getRemainingWeightGrams()).isEqualByComparingTo("92.500");
            assertThat(metti.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
        }

        @Test
        @DisplayName("puts an emptied box back on sale")
        void reopensAnEmptiedBox() {
            InventoryItem metti = box("10.000");
            metti.billOut(new BigDecimal("10.000"));
            assertThat(metti.getStatus()).isEqualTo(InventoryStatus.SOLD);

            metti.returnToStock(new BigDecimal("10.000"));

            assertThat(metti.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
            assertThat(metti.getRemainingWeightGrams()).isEqualByComparingTo("10.000");
        }

        @Test
        @DisplayName("cannot inflate a box past what it held")
        void cannotInflateTheBox() {
            InventoryItem metti = box("92.500");
            metti.billOut(new BigDecimal("4.100"));

            // Two cancellations of the same line - which the invoice guards
            // against, but the box should not depend on that to stay honest.
            metti.returnToStock(new BigDecimal("4.100"));
            metti.returnToStock(new BigDecimal("4.100"));

            assertThat(metti.getRemainingWeightGrams()).isEqualByComparingTo("92.500");
        }

        @Test
        @DisplayName("restores a single article whole, whatever weight it is handed")
        void restoresASingleArticleWhole() {
            InventoryItem band = ring("5.250");
            band.billOut(new BigDecimal("5.250"));

            // The line's weight and the piece's weight are the same figure, so
            // adding would be one more place for them to drift.
            band.returnToStock(new BigDecimal("5.250"));

            assertThat(band.getRemainingWeightGrams()).isEqualByComparingTo("5.250");
            assertThat(band.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
        }
    }
}
