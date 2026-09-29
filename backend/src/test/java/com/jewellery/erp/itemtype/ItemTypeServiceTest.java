package com.jewellery.erp.itemtype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.itemtype.dto.ItemTypeRequest;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.itemtype.mapper.ItemTypeMapper;
import com.jewellery.erp.itemtype.repository.ItemTypeRepository;
import com.jewellery.erp.itemtype.service.ItemTypeService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The master-module pattern: normalise, check uniqueness, deactivate rather than
 * delete, and refuse a delete that would orphan something.
 */
@ExtendWith(MockitoExtension.class)
class ItemTypeServiceTest {

    @Mock private ItemTypeRepository itemTypeRepository;

    private ItemTypeService service;

    @BeforeEach
    void setUp() {
        service = new ItemTypeService(itemTypeRepository, new ItemTypeMapper());
    }

    private static ItemType gold() {
        ItemType itemType = new ItemType();
        itemType.setId(1L);
        itemType.setName("Gold");
        itemType.setCode("GOLD");
        itemType.setActive(true);
        return itemType;
    }

    @Test
    @DisplayName("surrounding whitespace does not create a second Gold")
    void normalisesNameAndCodeBeforeComparing() {
        when(itemTypeRepository.existsByNameIgnoreCase("Gold")).thenReturn(false);
        when(itemTypeRepository.existsByCodeIgnoreCase("GOLD")).thenReturn(false);
        when(itemTypeRepository.save(any(ItemType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new ItemTypeRequest("  Gold  ", " gold ", null, null));

        ArgumentCaptor<ItemType> saved = ArgumentCaptor.forClass(ItemType.class);
        verify(itemTypeRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Gold");
        assertThat(saved.getValue().getCode()).isEqualTo("GOLD");
    }

    @Test
    @DisplayName("a duplicate name is rejected before anything is written")
    void rejectsDuplicateName() {
        // The service normalises whitespace but preserves case, and delegates the
        // case-insensitive comparison to the repository query. That query is
        // mocked here, so this asserts the rejection path - not the collation.
        when(itemTypeRepository.existsByNameIgnoreCase("gold")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new ItemTypeRequest("gold", "GLD", null, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(itemTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("only active item types are offered for selection")
    void lookupReturnsActiveOnly() {
        when(itemTypeRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(gold()));

        assertThat(service.findActiveLookup()).singleElement().satisfies(lookup -> {
            assertThat(lookup.name()).isEqualTo("Gold");
            assertThat(lookup.code()).isEqualTo("GOLD");
        });
    }

    @Test
    @DisplayName("an inactive item type cannot be referenced by new stock")
    void requireActiveRejectsInactive() {
        ItemType inactive = gold();
        inactive.setActive(false);
        when(itemTypeRepository.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.requireActive(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("an item type with purities cannot be deleted, only deactivated")
    void refusesDeletingReferencedItemType() {
        when(itemTypeRepository.findById(1L)).thenReturn(Optional.of(gold()));
        when(itemTypeRepository.countPurities(1L)).thenReturn(4L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ReferencedRecordException.class)
                .hasMessageContaining("Deactivate it instead");

        // Typed matcher: the repository also inherits delete(Specification), so a
        // bare any() cannot tell the two overloads apart.
        verify(itemTypeRepository, never()).delete(any(ItemType.class));
    }

    @Test
    @DisplayName("deactivation stays possible even while stock references the item type")
    void allowsDeactivationOfReferencedItemType() {
        ItemType itemType = gold();
        when(itemTypeRepository.findById(1L)).thenReturn(Optional.of(itemType));
        when(itemTypeRepository.countPurities(1L)).thenReturn(4L);

        assertThat(service.updateStatus(1L, false).active()).isFalse();
        assertThat(itemType.isActive()).isFalse();
    }

    @Test
    @DisplayName("an unknown id is a 404, not a null")
    void reportsMissingItemType() {
        when(itemTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
