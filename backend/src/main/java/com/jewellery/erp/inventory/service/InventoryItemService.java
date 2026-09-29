package com.jewellery.erp.inventory.service;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.category.service.CategoryService;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.StateConflictException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.hsn.service.HsnCodeService;
import com.jewellery.erp.inventory.dto.InventoryItemDto;
import com.jewellery.erp.inventory.dto.InventoryItemFilter;
import com.jewellery.erp.inventory.dto.InventoryItemRequest;
import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.inventory.entity.InventoryStatus;
import com.jewellery.erp.inventory.mapper.InventoryItemMapper;
import com.jewellery.erp.inventory.repository.InventoryItemRepository;
import com.jewellery.erp.inventory.spec.InventoryItemSpecifications;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.itemtype.service.ItemTypeService;
import com.jewellery.erp.purity.service.PurityService;
import com.jewellery.erp.subcategory.service.SubCategoryService;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inventory of individual jewellery pieces.
 *
 * <p>Two rules are the reason this service exists rather than a generic CRUD
 * handler, and neither can be expressed as a column constraint:
 *
 * <ol>
 *   <li><b>The purity must belong to the chosen item type.</b> A 22K gold purity
 *       on a silver chain is not a typo the shop can live with - it corrupts
 *       every pure-weight and valuation calculation built on top of it.
 *   <li><b>The sub category must belong to the chosen category.</b> Same
 *       reasoning applied to classification.
 * </ol>
 *
 * <p>Both are checked on create and on update. The frontend cascades the
 * dropdowns so a user cannot normally produce a mismatch, but the API is
 * reachable without the frontend.
 */
@Service
@Transactional(readOnly = true)
public class InventoryItemService {

    private static final Logger log = LoggerFactory.getLogger(InventoryItemService.class);
    private static final int SERIAL_NUMBER_LENGTH = 6;
    private static final int MAX_SERIAL_NUMBER = 999_999;

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemTypeService itemTypeService;
    private final PurityService purityService;
    private final CategoryService categoryService;
    private final SubCategoryService subCategoryService;
    private final HsnCodeService hsnCodeService;
    private final InventoryItemMapper inventoryItemMapper;

    public InventoryItemService(
            InventoryItemRepository inventoryItemRepository,
            ItemTypeService itemTypeService,
            PurityService purityService,
            CategoryService categoryService,
            SubCategoryService subCategoryService,
            HsnCodeService hsnCodeService,
            InventoryItemMapper inventoryItemMapper) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.itemTypeService = itemTypeService;
        this.purityService = purityService;
        this.categoryService = categoryService;
        this.subCategoryService = subCategoryService;
        this.hsnCodeService = hsnCodeService;
        this.inventoryItemMapper = inventoryItemMapper;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<InventoryItemDto> findAll(InventoryItemFilter filter, Pageable pageable) {
        return PageResponse.from(
                inventoryItemRepository.findAll(InventoryItemSpecifications.matching(filter), pageable),
                inventoryItemMapper::toDto);
    }

    public InventoryItemDto findById(Long id) {
        return inventoryItemMapper.toDto(requireItem(id));
    }

    /** Lookup by the business key - what a barcode scan or a counter enquiry uses. */
    public InventoryItemDto findBySerialNumber(String serialNumber) {
        return inventoryItemRepository
                .findBySerialNumber(normalizeSerial(serialNumber))
                .map(inventoryItemMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No inventory item with serial number %s.".formatted(serialNumber)));
    }

    /**
     * Suggests the next free serial number, zero padded.
     *
     * <p>A suggestion, not a reservation: two people opening the form at once see
     * the same number, and whoever saves second is rejected by the uniqueness
     * check with a clear message. Reserving numbers would leave gaps whenever a
     * form is abandoned, which a physical serial sequence cannot have.
     */
    public String suggestNextSerialNumber() {
        int next = inventoryItemRepository.findHighestSerialNumber() + 1;
        if (next > MAX_SERIAL_NUMBER) {
            throw new BusinessRuleException(
                    "The 6 digit serial number range is exhausted. Serial numbers must be widened before "
                            + "more stock can be added.");
        }
        return pad(next);
    }

    /** Dashboard metrics, gathered in one place so the controller stays thin. */
    public InventorySummary summarise() {
        BigDecimal activeWeight = inventoryItemRepository.sumActiveWeightGrams();
        return new InventorySummary(
                inventoryItemRepository.count(),
                // "In stock" now means unsold as well as active.
                inventoryItemRepository.countByActiveTrueAndStatus(InventoryStatus.AVAILABLE),
                // Null when the shop holds no active stock at all.
                activeWeight == null ? BigDecimal.ZERO : activeWeight);
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public InventoryItemDto create(InventoryItemRequest request) {
        String serialNumber = normalizeSerial(request.serialNumber());

        if (inventoryItemRepository.existsBySerialNumber(serialNumber)) {
            throw new DuplicateResourceException(
                    "serialNumber", "Serial number %s already exists.".formatted(serialNumber));
        }

        InventoryItem entity = new InventoryItem();
        entity.setSerialNumber(serialNumber);
        applyReferences(entity, request);
        applyAttributes(entity, request);
        entity.setActive(request.active() == null || request.active());

        InventoryItem saved = inventoryItemRepository.save(entity);
        log.info("Inventory item {} added ({} {}, {}g)",
                saved.getSerialNumber(),
                saved.getPurity().getName(),
                saved.getItemType().getName(),
                saved.getWeightGrams());
        return inventoryItemMapper.toDto(saved);
    }

    @Transactional
    public InventoryItemDto update(Long id, InventoryItemRequest request) {
        InventoryItem entity = requireItem(id);
        requireNotSold(entity, "edited");
        String serialNumber = normalizeSerial(request.serialNumber());

        if (inventoryItemRepository.existsBySerialNumberAndIdNot(serialNumber, id)) {
            throw new DuplicateResourceException(
                    "serialNumber", "Serial number %s already exists.".formatted(serialNumber));
        }

        entity.setSerialNumber(serialNumber);
        applyReferences(entity, request);
        applyAttributes(entity, request);
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        log.info("Inventory item {} updated", entity.getSerialNumber());
        return inventoryItemMapper.toDto(entity);
    }

    /**
     * Deactivation - the normal way a piece leaves the active list.
     *
     * <p>Once sales exist this becomes the mechanism a sale uses, which is why the
     * row is never removed: the invoice has to keep pointing at the exact piece.
     */
    @Transactional
    public InventoryItemDto updateStatus(Long id, boolean active) {
        InventoryItem entity = requireItem(id);
        entity.setActive(active);
        log.info("Inventory item {} {}", entity.getSerialNumber(), active ? "activated" : "deactivated");
        return inventoryItemMapper.toDto(entity);
    }

    /**
     * Hard delete, for correcting a mis-keyed entry.
     *
     * <p>Kept because a shop that types the wrong serial number at stock-in needs a
     * way to remove the phantom piece. Refused for anything an invoice has ever
     * referenced - including a cancelled one - because that invoice must keep
     * pointing at a real piece.
     */
    @Transactional
    public void delete(Long id) {
        InventoryItem entity = requireItem(id);
        requireNotSold(entity, "deleted");
        if (inventoryItemRepository.countSaleLines(id) > 0) {
            throw ReferencedRecordException.of(
                    "Inventory item", entity.getSerialNumber(), "invoice line(s), including cancelled invoices");
        }
        inventoryItemRepository.delete(entity);
        log.info("Inventory item {} deleted", entity.getSerialNumber());
    }

    // ------------------------------------------------------------ for sales ---
    //
    // The sales module reaches inventory only through these methods, never through
    // the repository - the same rule every other cross-module call follows.

    /**
     * The piece behind a serial number, checked for sale.
     *
     * <p>Used by the counter lookup and the calculation preview. It does not lock;
     * {@link #lockSellable} repeats every check under a row lock at completion.
     */
    public InventoryItem requireSellable(String serialNumber) {
        String serial = normalizeSerial(serialNumber);
        InventoryItem item = inventoryItemRepository
                .findBySerialNumber(serial)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                        "No inventory item with serial number %s.".formatted(serial)));
        assertSellable(item);
        return item;
    }

    /**
     * Locks the pieces for the current transaction and verifies each is still sellable.
     *
     * <p>{@code MANDATORY}: a lock taken outside the sale's own transaction would be
     * released before the sale commits, which would defeat the point.
     *
     * @return the locked pieces, keyed by canonical serial number
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, InventoryItem> lockSellable(Collection<String> serialNumbers) {
        List<String> serials = serialNumbers.stream().map(InventoryItemService::normalizeSerial).toList();

        Map<String, Long> idsBySerial = inventoryItemRepository.findAllBySerialNumberIn(serials).stream()
                .collect(Collectors.toMap(InventoryItem::getSerialNumber, InventoryItem::getId));
        for (String serial : serials) {
            if (!idsBySerial.containsKey(serial)) {
                throw new ResourceNotFoundException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                        "No inventory item with serial number %s.".formatted(serial));
            }
        }

        Map<String, InventoryItem> locked = new LinkedHashMap<>();
        for (InventoryItem item : inventoryItemRepository.lockAllById(idsBySerial.values())) {
            // Re-checked under the lock: a sale that committed while we waited has
            // already flipped the status.
            assertSellable(item);
            locked.put(item.getSerialNumber(), item);
        }
        return locked;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markSold(Collection<InventoryItem> items) {
        items.forEach(item -> item.setStatus(InventoryStatus.SOLD));
    }

    /** Puts pieces back on sale after their invoice is cancelled. Locks them first. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void returnToStock(Collection<Long> inventoryItemIds) {
        inventoryItemRepository.lockAllById(inventoryItemIds)
                .forEach(item -> item.setStatus(InventoryStatus.AVAILABLE));
    }

    /** Canonical six-digit form of a serial number, for callers outside this module. */
    public static String canonicalSerial(String raw) {
        return normalizeSerial(raw);
    }

    private static void assertSellable(InventoryItem item) {
        if (item.getStatus() == InventoryStatus.SOLD) {
            throw new StateConflictException(ErrorCode.ITEM_ALREADY_SOLD, "serialNumber",
                    "Serial number %s has already been sold.".formatted(item.getSerialNumber()));
        }
        if (!item.isActive()) {
            throw new BusinessRuleException(ErrorCode.INVENTORY_ITEM_INACTIVE, "serialNumber",
                    "Serial number %s is inactive and cannot be sold.".formatted(item.getSerialNumber()));
        }
        if (item.getHsnCode() == null) {
            // The HSN code carries the GST rate; without it the tax cannot be computed.
            throw new BusinessRuleException(ErrorCode.INVENTORY_ITEM_NOT_BILLABLE, "serialNumber",
                    "Serial number %s has no HSN code. Set one on the inventory item before billing it."
                            .formatted(item.getSerialNumber()));
        }
    }

    private static void requireNotSold(InventoryItem item, String action) {
        if (item.getStatus() == InventoryStatus.SOLD) {
            throw new StateConflictException(ErrorCode.ITEM_ALREADY_SOLD,
                    "Serial number %s has been sold and can no longer be %s.".formatted(item.getSerialNumber(), action));
        }
    }

    // ------------------------------------------------------------- helpers ---

    /**
     * Resolves and validates every master reference on the request.
     *
     * <p>This is where the two cross-field rules are enforced: each dependent
     * reference is resolved <em>in the context of</em> its parent, so a mismatch
     * cannot pass.
     */
    private void applyReferences(InventoryItem entity, InventoryItemRequest request) {
        ItemType itemType = itemTypeService.requireActive(request.itemTypeId());
        Category category = categoryService.requireActive(request.categoryId());

        entity.setItemType(itemType);
        entity.setCategory(category);
        entity.setPurity(purityService.requireActiveForItemType(request.purityId(), itemType.getId()));

        entity.setSubCategory(request.subCategoryId() == null
                ? null
                : subCategoryService.requireActiveInCategory(request.subCategoryId(), category.getId()));

        entity.setHsnCode(request.hsnId() == null ? null : hsnCodeService.requireActive(request.hsnId()));
    }

    private void applyAttributes(InventoryItem entity, InventoryItemRequest request) {
        entity.setSize(StringNormalizer.trimToNull(request.size()));
        entity.setWeightGrams(request.weightGrams());
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
    }

    /**
     * Normalises a serial number to its canonical 6 digit form.
     *
     * <p>Accepts "123" from a keyboard and stores "000123", so the same physical
     * piece cannot be entered twice under two spellings of the same number.
     */
    private static String normalizeSerial(String raw) {
        String trimmed = StringNormalizer.trimToNull(raw);
        if (trimmed == null) {
            throw new BusinessRuleException("serialNumber", "Serial number is required.");
        }
        if (!trimmed.matches("^[0-9]{1,6}$")) {
            throw new BusinessRuleException(
                    "serialNumber", "Serial number must contain only digits, up to 6 of them.");
        }
        return pad(Integer.parseInt(trimmed));
    }

    private static String pad(int value) {
        return String.format("%0" + SERIAL_NUMBER_LENGTH + "d", value);
    }

    private InventoryItem requireItem(Long id) {
        return inventoryItemRepository
                .findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Inventory item", id));
    }

    /** Aggregate counts for the dashboard. */
    public record InventorySummary(long totalItems, long activeItems, BigDecimal activeWeightGrams) {}
}
