package com.jewellery.erp.itemtype.service;

import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.repository.NamedMasterSpecifications;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.itemtype.dto.ItemTypeDto;
import com.jewellery.erp.itemtype.dto.ItemTypeRequest;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.itemtype.mapper.ItemTypeMapper;
import com.jewellery.erp.itemtype.repository.ItemTypeRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Item type master.
 *
 * <p>Establishes the pattern every master module in this application follows:
 *
 * <ul>
 *   <li>normalise input before comparing it, so " gold " and "Gold" collide as
 *       the user expects;
 *   <li>check uniqueness in the service for a good error message, and let the
 *       database unique index be the actual guarantee under concurrency;
 *   <li>deactivate rather than delete, and refuse a hard delete outright once
 *       anything references the record.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ItemTypeService {

    private static final Logger log = LoggerFactory.getLogger(ItemTypeService.class);

    private final ItemTypeRepository itemTypeRepository;
    private final ItemTypeMapper itemTypeMapper;

    public ItemTypeService(ItemTypeRepository itemTypeRepository, ItemTypeMapper itemTypeMapper) {
        this.itemTypeRepository = itemTypeRepository;
        this.itemTypeMapper = itemTypeMapper;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<ItemTypeDto> findAll(MasterFilter filter, Pageable pageable) {
        return PageResponse.from(
                itemTypeRepository.findAll(NamedMasterSpecifications.matching(filter), pageable),
                itemTypeMapper::toDto);
    }

    public ItemTypeDto findById(Long id) {
        ItemType entity = requireItemType(id);
        return itemTypeMapper.toDto(entity, itemTypeRepository.countPurities(id));
    }

    /** Dropdown source. Returns active records only. */
    public List<LookupDto> findActiveLookup() {
        return itemTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(itemTypeMapper::toLookup)
                .toList();
    }

    /** Resolves a reference for another module; the record must exist and be active. */
    public ItemType requireActive(Long id) {
        ItemType itemType = requireItemType(id);
        if (!itemType.isActive()) {
            throw new BusinessRuleException(
                    "itemTypeId", "Item type '%s' is inactive and cannot be used.".formatted(itemType.getName()));
        }
        return itemType;
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public ItemTypeDto create(ItemTypeRequest request) {
        String name = StringNormalizer.normalizeName(request.name());
        String code = StringNormalizer.normalizeCode(request.code());

        if (itemTypeRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException(
                    "name", "An item type named '%s' already exists.".formatted(name));
        }
        if (itemTypeRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("code", "Item type code '%s' is already in use.".formatted(code));
        }

        ItemType entity = new ItemType();
        entity.setName(name);
        entity.setCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        entity.setActive(request.active() == null || request.active());

        ItemType saved = itemTypeRepository.save(entity);
        log.info("Item type '{}' created", saved.getCode());
        return itemTypeMapper.toDto(saved);
    }

    @Transactional
    public ItemTypeDto update(Long id, ItemTypeRequest request) {
        ItemType entity = requireItemType(id);
        String name = StringNormalizer.normalizeName(request.name());
        String code = StringNormalizer.normalizeCode(request.code());

        if (itemTypeRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException(
                    "name", "An item type named '%s' already exists.".formatted(name));
        }
        if (itemTypeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("code", "Item type code '%s' is already in use.".formatted(code));
        }

        entity.setName(name);
        entity.setCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        log.info("Item type '{}' updated", entity.getCode());
        return itemTypeMapper.toDto(entity, itemTypeRepository.countPurities(id));
    }

    /**
     * Deactivation is always permitted, including for a referenced record: the
     * existing inventory keeps its reference and stays readable, while the item
     * type disappears from every dropdown.
     */
    @Transactional
    public ItemTypeDto updateStatus(Long id, boolean active) {
        ItemType entity = requireItemType(id);
        entity.setActive(active);
        log.info("Item type '{}' {}", entity.getCode(), active ? "activated" : "deactivated");
        return itemTypeMapper.toDto(entity, itemTypeRepository.countPurities(id));
    }

    /** Hard delete, permitted only while nothing references the record. */
    @Transactional
    public void delete(Long id) {
        ItemType entity = requireItemType(id);

        long purities = itemTypeRepository.countPurities(id);
        if (purities > 0) {
            throw ReferencedRecordException.of("Item type", entity.getName(), "%d purity record(s)".formatted(purities));
        }
        long items = itemTypeRepository.countInventoryItems(id);
        if (items > 0) {
            throw ReferencedRecordException.of("Item type", entity.getName(), "%d inventory item(s)".formatted(items));
        }

        itemTypeRepository.delete(entity);
        log.info("Item type '{}' deleted", entity.getCode());
    }

    // ------------------------------------------------------------- helpers ---

    private ItemType requireItemType(Long id) {
        return itemTypeRepository
                .findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Item type", id));
    }
}
