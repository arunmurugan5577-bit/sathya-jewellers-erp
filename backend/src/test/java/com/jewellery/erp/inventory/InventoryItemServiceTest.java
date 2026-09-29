package com.jewellery.erp.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.category.service.CategoryService;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.hsn.service.HsnCodeService;
import com.jewellery.erp.inventory.dto.InventoryItemRequest;
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
class InventoryItemServiceTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private ItemTypeService itemTypeService;
    @Mock private PurityService purityService;
    @Mock private CategoryService categoryService;
    @Mock private SubCategoryService subCategoryService;
    @Mock private HsnCodeService hsnCodeService;

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
                new InventoryItemMapper());

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
        @DisplayName("a duplicate is rejected with a message naming the number")
        void rejectsDuplicateSerialNumber() {
            when(inventoryItemRepository.existsBySerialNumber("123456")).thenReturn(true);

            assertThatThrownBy(() -> service.create(request("123456")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessage("Serial number 123456 already exists.")
                    .extracting(exception -> ((DuplicateResourceException) exception).getField())
                    .isEqualTo("serialNumber");

            verify(inventoryItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("leading zeros are preserved, not swallowed by a numeric conversion")
        void preservesLeadingZeros() {
            stubValidReferences();
            when(inventoryItemRepository.existsBySerialNumber("000001")).thenReturn(false);
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.create(request("000001"));

            ArgumentCaptor<InventoryItem> saved = ArgumentCaptor.forClass(InventoryItem.class);
            verify(inventoryItemRepository).save(saved.capture());
            assertThat(saved.getValue().getSerialNumber()).isEqualTo("000001");
        }

        @Test
        @DisplayName("a short number is padded so the same piece cannot be entered twice")
        void padsShortSerialNumber() {
            stubValidReferences();
            when(inventoryItemRepository.existsBySerialNumber("000042")).thenReturn(false);
            when(inventoryItemRepository.save(any(InventoryItem.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.create(request("42"));

            ArgumentCaptor<InventoryItem> saved = ArgumentCaptor.forClass(InventoryItem.class);
            verify(inventoryItemRepository).save(saved.capture());
            assertThat(saved.getValue().getSerialNumber()).isEqualTo("000042");
        }

        @Test
        @DisplayName("more than six digits is rejected")
        void rejectsTooLongSerialNumber() {
            assertThatThrownBy(() -> service.create(request("1234567")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("up to 6");
        }

        @Test
        @DisplayName("a non-numeric serial number is rejected")
        void rejectsNonNumericSerialNumber() {
            assertThatThrownBy(() -> service.create(request("12A456")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("only digits");
        }

        @Test
        @DisplayName("the suggested next serial continues the sequence, zero padded")
        void suggestsNextSerialNumber() {
            when(inventoryItemRepository.findHighestSerialNumber()).thenReturn(41);

            assertThat(service.suggestNextSerialNumber()).isEqualTo("000042");
        }

        @Test
        @DisplayName("exhausting the six digit range is reported, not wrapped around")
        void refusesWhenSerialRangeExhausted() {
            when(inventoryItemRepository.findHighestSerialNumber()).thenReturn(999_999);

            assertThatThrownBy(() -> service.suggestNextSerialNumber())
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
            existing.setSerialNumber("000123");

            when(inventoryItemRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
            when(inventoryItemRepository.existsBySerialNumberAndIdNot("000123", 5L)).thenReturn(false);
            stubValidReferences();

            assertThat(service.update(5L, request("000123")).serialNumber()).isEqualTo("000123");
        }

        @Test
        @DisplayName("taking a serial number that belongs to another row is rejected")
        void rejectsSerialNumberOwnedByAnotherRow() {
            InventoryItem existing = new InventoryItem();
            existing.setId(5L);
            existing.setSerialNumber("000123");

            when(inventoryItemRepository.findById(5L)).thenReturn(java.util.Optional.of(existing));
            when(inventoryItemRepository.existsBySerialNumberAndIdNot("000999", 5L)).thenReturn(true);

            assertThatThrownBy(() -> service.update(5L, request("000999")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("000999");
        }
    }
}
