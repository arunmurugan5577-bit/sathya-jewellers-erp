package com.jewellery.erp.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import com.jewellery.erp.numbering.DocumentSeries;
import static org.mockito.Mockito.when;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.category.service.CategoryService;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.hsn.service.HsnCodeService;
import com.jewellery.erp.inventory.dto.InventoryItemRequest;
import com.jewellery.erp.numbering.DocumentNumberService;
import com.jewellery.erp.numbering.SerialCounterService;
import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.inventory.mapper.InventoryItemMapper;
import com.jewellery.erp.inventory.repository.InventoryItemRepository;
import com.jewellery.erp.inventory.service.InventoryItemService;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.itemtype.service.ItemTypeService;
import com.jewellery.erp.purity.entity.Purity;
import com.jewellery.erp.purity.service.PurityService;
import com.jewellery.erp.subcategory.entity.SubCategory;
import com.jewellery.erp.subcategory.service.SubCategoryService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The rules that make inventory trustworthy: a unique, correctly formatted
 * serial number, and master references that are consistent with each other.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class InventoryItemServiceTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private ItemTypeService itemTypeService;
    @Mock private PurityService purityService;
    @Mock private CategoryService categoryService;
    @Mock private SubCategoryService subCategoryService;
    @Mock private HsnCodeService hsnCodeService;
    @Mock private DocumentNumberService documentNumberService;
    @Mock private SerialCounterService serialCounterService;

    private InventoryItemService service;

    private ItemType gold;
    private Category ring;

    @BeforeEach
    void setUp() {
        service = new InventoryItemService(
                inventoryItemRepository,
                itemTypeService,
                purityService,
                categoryService,
                subCategoryService,
                hsnCodeService,
                new InventoryItemMapper(),
                documentNumberService,
                serialCounterService);

        gold = new ItemType();
        gold.setId(1L);
        gold.setName("Gold");
        gold.setCode("GOLD");

        ring = new Category();
        ring.setId(10L);
        ring.setName("Ring");
        ring.setCode("RING");
    }

    private static InventoryItemRequest request(String serialNumber) {
        return new InventoryItemRequest(
                serialNumber, 1L, 2L, 10L, null, null, "16", new BigDecimal("5.250"), "Plain band", null);
    }

    private Purity purity916() {
        Purity purity = new Purity();
        purity.setId(2L);
        purity.setName("22K / 916");
        purity.setPurityValue(new BigDecimal("916.000"));
        purity.setItemType(gold);
        return purity;
    }

    private void stubValidReferences() {
        when(itemTypeService.requireActive(1L)).thenReturn(gold);
        when(categoryService.requireActive(10L)).thenReturn(ring);
        when(purityService.requireActiveForItemType(2L, 1L)).thenReturn(purity916());
    }

    @Nested
    @DisplayName("serial number")
    class SerialNumber {

        @Test
        @DisplayName("the counter issues the number; anything the caller sent is ignored")
        void theCounterIssuesTheSerialNumber() {
            stubValidReferences();
            when(documentNumberService.next(eq(DocumentSeries.INVENTORY_SERIAL), any()))
                    .thenReturn("001");
            when(inventoryItemRepository.existsBySerialNumber("001")).thenReturn(false);
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // The caller asks for 999999 and gets what the counter says.
            assertThat(service.create(request("999999")).serialNumber()).isEqualTo("001");
        }

        @Test
        @DisplayName("a number already on a piece is stepped over, not refused")
        void stepsOverTakenNumbers() {
            // The shop had tags before the counter existed, so a run can land on
            // numbers that are already out there. That is a fact about old stock,
            // not a mistake the person adding a piece can do anything about.
            stubValidReferences();
            when(documentNumberService.next(eq(DocumentSeries.INVENTORY_SERIAL), any()))
                    .thenReturn("001", "002", "003");
            when(inventoryItemRepository.existsBySerialNumber("001")).thenReturn(true);
            when(inventoryItemRepository.existsBySerialNumber("002")).thenReturn(true);
            when(inventoryItemRepository.existsBySerialNumber("003")).thenReturn(false);
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.create(request("000123")).serialNumber()).isEqualTo("003");
        }

        @Test
        @DisplayName("the next serial comes from the counter, zero padded")
        void peeksAtTheCounter() {
            when(serialCounterService.peek()).thenReturn(42L);
            when(inventoryItemRepository.existsBySerialNumber("042")).thenReturn(false);

            assertThat(service.peekNextSerialNumber()).isEqualTo("042");
        }

        @Test
        @DisplayName("the peek steps over numbers a piece already holds")
        void peekSkipsTakenNumbers() {
            // The shop had tags before the counter existed, so a run can land on
            // numbers that are already out there.
            when(serialCounterService.peek()).thenReturn(1L);
            when(inventoryItemRepository.existsBySerialNumber("001")).thenReturn(true);
            when(inventoryItemRepository.existsBySerialNumber("002")).thenReturn(true);
            when(inventoryItemRepository.existsBySerialNumber("003")).thenReturn(false);

            assertThat(service.peekNextSerialNumber()).isEqualTo("003");
        }

        @Test
        @DisplayName("padding is a floor: 999 is followed by 1000, not by an error")
        void paddingIsAFloorNotACap() {
            when(serialCounterService.peek()).thenReturn(999L);
            when(inventoryItemRepository.existsBySerialNumber("999")).thenReturn(false);
            assertThat(service.peekNextSerialNumber()).isEqualTo("999");

            when(serialCounterService.peek()).thenReturn(1000L);
            when(inventoryItemRepository.existsBySerialNumber("1000")).thenReturn(false);
            assertThat(service.peekNextSerialNumber()).isEqualTo("1000");

            when(serialCounterService.peek()).thenReturn(905_351L);
            when(inventoryItemRepository.existsBySerialNumber("905351")).thenReturn(false);
            assertThat(service.peekNextSerialNumber()).isEqualTo("905351");
        }

        @Test
        @DisplayName("exhausting the six digit range is reported, not wrapped around")
        void refusesWhenSerialRangeExhausted() {
            when(serialCounterService.peek()).thenReturn(1_000_000L);

            assertThatThrownBy(() -> service.peekNextSerialNumber())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("exhausted");
        }
    }

    @Nested
    @DisplayName("cross-field master rules")
    class MasterRules {

        @Test
        @DisplayName("a purity belonging to another item type is rejected")
        void rejectsPurityFromAnotherItemType() {
            when(documentNumberService.next(eq(DocumentSeries.INVENTORY_SERIAL), any()))
                    .thenReturn("000500");
            when(inventoryItemRepository.existsBySerialNumber(anyString())).thenReturn(false);
            when(itemTypeService.requireActive(1L)).thenReturn(gold);
            when(categoryService.requireActive(10L)).thenReturn(ring);
            when(purityService.requireActiveForItemType(2L, 1L))
                    .thenThrow(new BusinessRuleException(
                            "purityId", "Purity '925' belongs to Silver and cannot be used with the selected item type."));

            assertThatThrownBy(() -> service.create(request("000123")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("belongs to Silver");

            verify(inventoryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("a sub category belonging to another category is rejected")
        void rejectsSubCategoryFromAnotherCategory() {
            when(documentNumberService.next(eq(DocumentSeries.INVENTORY_SERIAL), any()))
                    .thenReturn("000500");
            when(inventoryItemRepository.existsBySerialNumber(anyString())).thenReturn(false);
            stubValidReferences();
            when(subCategoryService.requireActiveInCategory(99L, 10L))
                    .thenThrow(new BusinessRuleException(
                            "subCategoryId", "Sub category 'Jhumka' does not belong to the selected category."));

            InventoryItemRequest withForeignSubCategory = new InventoryItemRequest(
                    "000123", 1L, 2L, 10L, 99L, null, "16", new BigDecimal("5.250"), null, null);

            assertThatThrownBy(() -> service.create(withForeignSubCategory))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("does not belong to the selected category");

            verify(inventoryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("sub category and HSN are optional")
        void allowsOmittedOptionalReferences() {
            when(documentNumberService.next(eq(DocumentSeries.INVENTORY_SERIAL), any()))
                    .thenReturn("000500");
            stubValidReferences();
            when(inventoryItemRepository.existsBySerialNumber("000123")).thenReturn(false);
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.create(request("000123"));

            verify(subCategoryService, never()).requireActiveInCategory(anyLong(), anyLong());
            verify(hsnCodeService, never()).requireActive(anyLong());
        }

        @Test
        @DisplayName("the weight is stored exactly as entered, with no floating point drift")
        void storesWeightExactly() {
            when(documentNumberService.next(eq(DocumentSeries.INVENTORY_SERIAL), any()))
                    .thenReturn("000500");
            stubValidReferences();
            when(inventoryItemRepository.existsBySerialNumber("000123")).thenReturn(false);
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.create(request("000123"));

            ArgumentCaptor<InventoryItem> saved = ArgumentCaptor.forClass(InventoryItem.class);
            verify(inventoryItemRepository).save(saved.capture());
            assertThat(saved.getValue().getWeightGrams()).isEqualByComparingTo(new BigDecimal("5.250"));
            assertThat(saved.getValue().getWeightGrams().scale()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("keeping the same serial number on the same row is allowed")
        void allowsUnchangedSerialNumberOnSameRow() {
            InventoryItem existing = new InventoryItem();
            existing.setId(5L);
            existing.setSerialNumber("123");

            when(inventoryItemRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
            when(inventoryItemRepository.existsBySerialNumberAndIdNot("123", 5L)).thenReturn(false);
            stubValidReferences();

            // "000123" and "123" are the same piece: the padding is normalised
            // away so one tag cannot be entered under two spellings.
            assertThat(service.update(5L, request("000123")).serialNumber()).isEqualTo("123");
        }

        @Test
        @DisplayName("taking a serial number that belongs to another row is rejected")
        void rejectsSerialNumberOwnedByAnotherRow() {
            InventoryItem existing = new InventoryItem();
            existing.setId(5L);
            existing.setSerialNumber("123");

            when(inventoryItemRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
            when(inventoryItemRepository.existsBySerialNumberAndIdNot("999", 5L)).thenReturn(true);

            assertThatThrownBy(() -> service.update(5L, request("000999")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("999");
        }
    }
}
