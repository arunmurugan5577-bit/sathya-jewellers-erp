package com.jewellery.erp.purity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.itemtype.service.ItemTypeService;
import com.jewellery.erp.purity.dto.PurityRequest;
import com.jewellery.erp.purity.entity.Purity;
import com.jewellery.erp.purity.mapper.PurityMapper;
import com.jewellery.erp.purity.repository.PurityRepository;
import com.jewellery.erp.purity.service.PurityService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A purity belongs to exactly one item type, and the system has to keep it that
 * way - the alternative is silver stock carrying a gold fineness, which poisons
 * every valuation built on top of it.
 */
@ExtendWith(MockitoExtension.class)
class PurityServiceTest {

    @Mock private PurityRepository purityRepository;
    @Mock private ItemTypeService itemTypeService;

    private PurityService service;

    private ItemType gold;
    private ItemType silver;

    @BeforeEach
    void setUp() {
        service = new PurityService(purityRepository, itemTypeService, new PurityMapper());

        gold = new ItemType();
        gold.setId(1L);
        gold.setName("Gold");
        gold.setCode("GOLD");

        silver = new ItemType();
        silver.setId(2L);
        silver.setName("Silver");
        silver.setCode("SILV");
    }

    private Purity silver925() {
        Purity purity = new Purity();
        purity.setId(7L);
        purity.setName("925");
        purity.setPurityValue(new BigDecimal("925.000"));
        purity.setItemType(silver);
        purity.setActive(true);
        return purity;
    }

    @Test
    @DisplayName("a purity may only be used with the item type it belongs to")
    void rejectsPurityFromAnotherItemType() {
        when(purityRepository.findById(7L)).thenReturn(Optional.of(silver925()));

        assertThatThrownBy(() -> service.requireActiveForItemType(7L, gold.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("belongs to Silver");
    }

    @Test
    @DisplayName("a purity of the right item type is accepted")
    void acceptsMatchingItemType() {
        when(purityRepository.findById(7L)).thenReturn(Optional.of(silver925()));

        assertThat(service.requireActiveForItemType(7L, silver.getId()).getName()).isEqualTo("925");
    }

    @Test
    @DisplayName("an inactive purity cannot be selected, even for the right item type")
    void rejectsInactivePurity() {
        Purity inactive = silver925();
        inactive.setActive(false);
        when(purityRepository.findById(7L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.requireActiveForItemType(7L, silver.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("the same fineness may exist under two different item types")
    void allowsSameValueUnderDifferentItemTypes() {
        when(itemTypeService.requireActive(2L)).thenReturn(silver);
        when(purityRepository.existsByItemTypeAndNameIgnoreCase(2L, "999")).thenReturn(false);
        when(purityRepository.existsByItemTypeAndValue(2L, new BigDecimal("999.000"))).thenReturn(false);
        when(purityRepository.save(any(Purity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurityRequest request = new PurityRequest(2L, "999", new BigDecimal("999.000"), "Fine silver", null);

        assertThat(service.create(request).purityValue()).isEqualByComparingTo("999.000");
    }

    @Test
    @DisplayName("the same fineness twice under one item type is a data entry mistake")
    void rejectsDuplicateValueWithinItemType() {
        when(itemTypeService.requireActive(1L)).thenReturn(gold);
        when(purityRepository.existsByItemTypeAndNameIgnoreCase(1L, "22K")).thenReturn(false);
        when(purityRepository.existsByItemTypeAndValue(1L, new BigDecimal("916.000"))).thenReturn(true);

        PurityRequest request = new PurityRequest(1L, "22K", new BigDecimal("916.000"), null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already has a purity with value");

        verify(purityRepository, never()).save(any());
    }

    @Test
    @DisplayName("a purity cannot be created under an inactive item type")
    void rejectsInactiveItemType() {
        when(itemTypeService.requireActive(1L))
                .thenThrow(new BusinessRuleException("itemTypeId", "Item type 'Gold' is inactive and cannot be used."));

        PurityRequest request = new PurityRequest(1L, "22K", new BigDecimal("916.000"), null, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleException.class);
        verify(purityRepository, never()).save(any());
    }

    @Test
    @DisplayName("a purity already used by stock cannot be moved to another item type")
    void refusesReparentingWhenReferenced() {
        Purity existing = silver925();
        when(purityRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(itemTypeService.requireActive(1L)).thenReturn(gold);
        when(purityRepository.countInventoryItems(7L)).thenReturn(4L);

        PurityRequest movedToGold = new PurityRequest(1L, "925", new BigDecimal("925.000"), null, null);

        assertThatThrownBy(() -> service.update(7L, movedToGold))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be moved to another item type");
    }
}
