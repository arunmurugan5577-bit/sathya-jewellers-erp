package com.jewellery.erp.purity.service;

import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.itemtype.service.ItemTypeService;
import com.jewellery.erp.purity.dto.PurityDto;
import com.jewellery.erp.purity.dto.PurityFilter;
import com.jewellery.erp.purity.dto.PurityRequest;
import com.jewellery.erp.purity.entity.Purity;
import com.jewellery.erp.purity.mapper.PurityMapper;
import com.jewellery.erp.purity.repository.PurityRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purity master.
 *
 * <p>The reason this is a master table rather than an enum: a shop that starts
 * trading 14K gold, or a region that recognises a fineness this code has never
 * heard of, must be able to add it from the Masters screen. Hardcoding
 * 24K/22K/18K would make that a release.
 *
 * <p>Uniqueness is scoped to the item type in two ways - by name and by value -
 * because "999" is a legitimate purity for both gold and silver, while defining
 * the same fineness twice under one metal is always a data entry mistake.
 */
@Service
@Transactional(readOnly = true)
public class PurityService {

    private static final Logger log = LoggerFactory.getLogger(PurityService.class);

    private final PurityRepository purityRepository;
    private final ItemTypeService itemTypeService;
    private final PurityMapper purityMapper;

    public PurityService(
            PurityRepository purityRepository, ItemTypeService itemTypeService, PurityMapper purityMapper) {
        this.purityRepository = purityRepository;
        this.itemTypeService = itemTypeService;
        this.purityMapper = purityMapper;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<PurityDto> findAll(PurityFilter filter, Pageable pageable) {
        return PageResponse.from(purityRepository.findAll(matching(filter), pageable), purityMapper::toDto);
    }

    public PurityDto findById(Long id) {
        return purityMapper.toDto(requirePurity(id));
    }

    /**
     * Cascading dropdown source for the inventory form: the active purities of one
     * item type, highest fineness first.
     */
    public List<LookupDto> findActiveLookupByItemType(Long itemTypeId) {
        itemTypeService.requireActive(itemTypeId);
        return purityRepository.findByItemTypeIdAndActiveTrueOrderByPurityValueDesc(itemTypeId).stream()
                .map(purityMapper::toLookup)
                .toList();
    }

    /**
     * Resolves a purity and verifies it belongs to the expected item type.
     *
     * <p>This is the rule that stops a 22K gold purity being recorded against a
     * silver piece. The UI cascade makes it unlikely; this makes it impossible.
     */
    public Purity requireActiveForItemType(Long purityId, Long itemTypeId) {
        Purity purity = requirePurity(purityId);

        if (!purity.isActive()) {
            throw new BusinessRuleException(
                    "purityId", "Purity '%s' is inactive and cannot be used.".formatted(purity.getName()));
        }
        if (!purity.getItemType().getId().equals(itemTypeId)) {
            throw new BusinessRuleException(
                    "purityId",
                    "Purity '%s' belongs to %s and cannot be used with the selected item type."
                            .formatted(purity.getName(), purity.getItemType().getName()));
        }
        return purity;
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public PurityDto create(PurityRequest request) {
        ItemType itemType = itemTypeService.requireActive(request.itemTypeId());
        String name = StringNormalizer.normalizeName(request.name());

        if (purityRepository.existsByItemTypeAndNameIgnoreCase(itemType.getId(), name)) {
            throw new DuplicateResourceException(
                    "name", "%s already has a purity named '%s'.".formatted(itemType.getName(), name));
        }
        if (purityRepository.existsByItemTypeAndValue(itemType.getId(), request.purityValue())) {
            throw new DuplicateResourceException(
                    "purityValue",
                    "%s already has a purity with value %s.".formatted(itemType.getName(), request.purityValue()));
        }

        Purity entity = new Purity();
        entity.setItemType(itemType);
        entity.setName(name);
        entity.setPurityValue(request.purityValue());
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        entity.setActive(request.active() == null || request.active());

        Purity saved = purityRepository.save(entity);
        log.info("Purity '{}' created for item type '{}'", saved.getName(), itemType.getCode());
        return purityMapper.toDto(saved);
    }

    @Transactional
    public PurityDto update(Long id, PurityRequest request) {
        Purity entity = requirePurity(id);
        ItemType itemType = itemTypeService.requireActive(request.itemTypeId());
        String name = StringNormalizer.normalizeName(request.name());

        boolean movingToAnotherItemType = !entity.getItemType().getId().equals(itemType.getId());
        if (movingToAnotherItemType && purityRepository.countInventoryItems(id) > 0) {
            // Re-parenting would leave existing pieces holding a purity that no
            // longer matches their own item type.
            throw new BusinessRuleException(
                    "itemTypeId",
                    "This purity is already used by inventory items and cannot be moved to another item type.");
        }

        if (purityRepository.existsByItemTypeAndNameIgnoreCaseAndIdNot(itemType.getId(), name, id)) {
            throw new DuplicateResourceException(
                    "name", "%s already has a purity named '%s'.".formatted(itemType.getName(), name));
        }
        if (purityRepository.existsByItemTypeAndValueAndIdNot(itemType.getId(), request.purityValue(), id)) {
            throw new DuplicateResourceException(
                    "purityValue",
                    "%s already has a purity with value %s.".formatted(itemType.getName(), request.purityValue()));
        }

        entity.setItemType(itemType);
        entity.setName(name);
        entity.setPurityValue(request.purityValue());
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        log.info("Purity '{}' updated", entity.getName());
        return purityMapper.toDto(entity);
    }

    @Transactional
    public PurityDto updateStatus(Long id, boolean active) {
        Purity entity = requirePurity(id);
        entity.setActive(active);
        log.info("Purity '{}' {}", entity.getName(), active ? "activated" : "deactivated");
        return purityMapper.toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        Purity entity = requirePurity(id);

        long items = purityRepository.countInventoryItems(id);
        if (items > 0) {
            throw ReferencedRecordException.of("Purity", entity.getName(), "%d inventory item(s)".formatted(items));
        }

        purityRepository.delete(entity);
        log.info("Purity '{}' deleted", entity.getName());
    }

    // ------------------------------------------------------------- helpers ---

    private Specification<Purity> matching(PurityFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            if (filter.active() != null) {
                predicates.add(builder.equal(root.get("active"), filter.active()));
            }
            if (filter.itemTypeId() != null) {
                predicates.add(builder.equal(root.get("itemType").get("id"), filter.itemTypeId()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Purity requirePurity(Long id) {
        return purityRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Purity", id));
    }
}
