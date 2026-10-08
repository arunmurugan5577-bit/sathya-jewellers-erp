package com.jewellery.erp.inventory.service;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.category.service.CategoryService;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.numbering.DocumentNumberService;
import com.jewellery.erp.numbering.DocumentSeries;
import com.jewellery.erp.numbering.SerialCounterService;
import java.time.LocalDate;
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
    /**
     * Zero padded to this width, and no further.
     *
     * <p>001 ... 999, then 1000 and on to 999999 - the width is a floor, not
     * a cap. Padding is how the same piece is stopped from being entered as
     * both "1" and "001"; it is not what limits how many pieces there can be.
     */
    private static final int SERIAL_NUMBER_LENGTH = 3;
    private static final int MAX_SERIAL_NUMBER = 999_999;
    /** Enough to step over a legacy block of tags without looping for ever. */
    private static final int MAX_SERIAL_ATTEMPTS = 1000;

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemTypeService itemTypeService;
    private final PurityService purityService;
    private final CategoryService categoryService;
    private final SubCategoryService subCategoryService;
    private final HsnCodeService hsnCodeService;
    private final InventoryItemMapper inventoryItemMapper;
    private final DocumentNumberService documentNumberService;
    private final SerialCounterService serialCounterService;

    public InventoryItemService(
            InventoryItemRepository inventoryItemRepository,
            ItemTypeService itemTypeService,
            PurityService purityService,
            CategoryService categoryService,
            SubCategoryService subCategoryService,
            HsnCodeService hsnCodeService,
            InventoryItemMapper inventoryItemMapper,
            DocumentNumberService documentNumberService,
            SerialCounterService serialCounterService) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.itemTypeService = itemTypeService;
        this.purityService = purityService;
        this.categoryService = categoryService;
        this.subCategoryService = subCategoryService;
        this.hsnCodeService = hsnCodeService;
        this.inventoryItemMapper = inventoryItemMapper;
        this.documentNumberService = documentNumberService;
        this.serialCounterService = serialCounterService;
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
    /**
     * What the next piece will be numbered, without taking the number.
     *
     * <p>A peek, for the form to show. Two people opening the form together see
     * the same figure; the number that matters is the one taken under lock when
     * the piece is actually saved.
     */
    public String peekNextSerialNumber() {
        long next = serialCounterService.peek();
        // Skipped here too, so the form shows what will really be issued.
        while (next <= MAX_SERIAL_NUMBER && inventoryItemRepository.existsBySerialNumber(pad((int) next))) {
            next++;
        }
        if (next > MAX_SERIAL_NUMBER) {
            throw new BusinessRuleException(
                    "The 6 digit serial number range is exhausted. Serial numbers must be widened before "
                            + "more stock can be added.");
        }
        return pad((int) next);
    }

    /**
     * Takes the next serial, stepping over any that a piece already holds.
     *
     * <p>Numbers can already be in use: the shop had tags before the counter
     * existed, and an administrator may move the counter back over them. Those
     * are stepped over rather than refused, because a clash is a fact about old
     * stock and not a mistake the person adding a piece can do anything about.
     */
    private String issueSerialNumber() {
        for (int attempt = 0; attempt < MAX_SERIAL_ATTEMPTS; attempt++) {
            String candidate = documentNumberService.next(DocumentSeries.INVENTORY_SERIAL, LocalDate.now());
            if (!inventoryItemRepository.existsBySerialNumber(candidate)) {
                return candidate;
            }
            log.info("Serial number {} is already taken; stepping past it", candidate);
        }
        throw new BusinessRuleException(
                "Could not find a free serial number after " + MAX_SERIAL_ATTEMPTS + " tries. "
                        + "Move the starting number past the serials already in stock.");
    }

    /** Dashboard metrics, gathered in one place so the controller stays thin. */
    public InventorySummary summarise() {
        BigDecimal activeWeight = inventoryItemRepository.sumRemainingWeightGrams();
        return new InventorySummary(
                inventoryItemRepository.count(),
                // "In stock" now means unsold as well as active.
                inventoryItemRepository.countByActiveTrueAndStatus(InventoryStatus.AVAILABLE),
                // Null when the shop holds no active stock at all.
                activeWeight == null ? BigDecimal.ZERO : activeWeight);
    }

    // ------------------------------------------------------------ commands ---

    /**
     * Adds a piece. The serial number is issued here, not supplied.
     *
     * <p>A serial identifies one physical piece and is printed on its tag as a
     * barcode, so a duplicate puts the same barcode on two pieces. The counter
     * is locked and incremented inside this transaction: a piece that fails to
     * save gives its number back rather than leaving a hole in the run.
     */
    @Transactional
    public InventoryItemDto create(InventoryItemRequest request) {
        String serialNumber = issueSerialNumber();

        InventoryItem entity = new InventoryItem();
        entity.setSerialNumber(serialNumber);
        entity.setBulk(Boolean.TRUE.equals(request.bulk()));
        applyReferences(entity, request);
        applyAttributes(entity, request);
        // Nothing has been billed yet, so the whole piece - or the whole box - is there.
        entity.setRemainingWeightGrams(entity.getWeightGrams());
        entity.setActive(request.active() == null || request.active());

        InventoryItem saved = inventoryItemRepository.save(entity);
        log.info("Inventory item {} added ({} {}, {}g{})",
                saved.getSerialNumber(),
                saved.getPurity().getName(),
                saved.getItemType().getName(),
                saved.getWeightGrams(),
                saved.isBulk() ? ", bulk" : "");
        return inventoryItemMapper.toDto(saved);
    }

    @Transactional
    public InventoryItemDto update(Long id, InventoryItemRequest request) {
        InventoryItem entity = requireItem(id);
        requireNotSold(entity, "edited");
        requireWeightStillOpen(entity, request);
        // Read before anything is written: once the new weight is applied, the
        // two figures match again and the question cannot be asked.
        boolean partlySold = entity.isPartlySold();
        String serialNumber = normalizeSerial(request.serialNumber());

        if (inventoryItemRepository.existsBySerialNumberAndIdNot(serialNumber, id)) {
            throw new DuplicateResourceException(
                    "serialNumber", "Serial number %s already exists.".formatted(serialNumber));
        }

        entity.setSerialNumber(serialNumber);
        entity.setBulk(Boolean.TRUE.equals(request.bulk()));
        applyReferences(entity, request);
        applyAttributes(entity, request);
        if (!partlySold) {
            // Nothing has been billed off this piece, so correcting its weight
            // corrects what is left of it too. A part-sold box is left alone:
            // requireWeightStillOpen has already refused any change to its
            // weight, and the grams it has given out are not this form's to
            // reinstate - that happens by cancelling the invoice that took them.
            entity.setRemainingWeightGrams(entity.getWeightGrams());
        }
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

    /**
     * Checks that a piece can give up the weight an invoice line asks of it, and
     * answers with the weight that will be billed.
     *
     * <p>Separate from taking it, because the preview prices an invoice that is
     * not being saved and must refuse an over-sale with the same message the save
     * would give. The piece must already be known sellable.
     */
    public BigDecimal assertBillable(InventoryItem item, BigDecimal requestedGrams) {
        if (!item.isBulk()) {
            if (requestedGrams != null) {
                // Ignoring it silently would let a counter believe it had sold 2g
                // of a 10g ring, when the customer is charged for all 10.
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "items",
                        ("Serial number %s is a single piece and is sold whole; "
                                + "a weight cannot be entered for it.").formatted(item.getSerialNumber()));
            }
            return item.getWeightGrams();
        }
        if (requestedGrams == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "items",
                    "Bulk item %s is sold by weight. Enter the weight being sold."
                            .formatted(item.getSerialNumber()));
        }
        BigDecimal wanted = item.billableWeight(requestedGrams);
        if (wanted.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "items",
                    "The weight sold from bulk item %s must be more than zero."
                            .formatted(item.getSerialNumber()));
        }
        if (wanted.compareTo(item.getRemainingWeightGrams()) > 0) {
            throw new StateConflictException(ErrorCode.INVENTORY_WEIGHT_EXCEEDED, "items",
                    "Only %sg is left in bulk item %s; %sg was asked for."
                            .formatted(item.getRemainingWeightGrams(), item.getSerialNumber(), wanted));
        }
        return wanted;
    }

    /**
     * Takes the billed weight off each piece.
     *
     * <p>A single article empties in one step and becomes SOLD; a bulk box stays
     * sellable until its last gram goes. The caller holds the locks from
     * {@link #lockSellable} and has been through {@link #assertBillable}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void billOut(Collection<Billed> billed) {
        for (Billed line : billed) {
            line.piece().billOut(line.weightGrams());
        }
    }

    /**
     * Puts weight back on sale after an invoice is cancelled. Locks the pieces first.
     *
     * @param weightsByItemId how much each piece gave to the cancelled invoice
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void returnToStock(Map<Long, BigDecimal> weightsByItemId) {
        for (InventoryItem item : inventoryItemRepository.lockAllById(weightsByItemId.keySet())) {
            item.returnToStock(weightsByItemId.get(item.getId()));
        }
    }

    /** One invoice line's claim on a piece: how much of it is going out. */
    public record Billed(InventoryItem piece, BigDecimal weightGrams) {}

    /** Canonical six-digit form of a serial number, for callers outside this module. */
    public static String canonicalSerial(String raw) {
        return normalizeSerial(raw);
    }

    private static void assertSellable(InventoryItem item) {
        if (item.getStatus() == InventoryStatus.SOLD) {
            throw new StateConflictException(ErrorCode.ITEM_ALREADY_SOLD, "serialNumber",
                    item.isBulk()
                            ? "Bulk item %s is empty - its whole weight has been sold."
                                    .formatted(item.getSerialNumber())
                            : "Serial number %s has already been sold.".formatted(item.getSerialNumber()));
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

    /**
     * Refuses to rewrite a weight that invoices have already been drawn from.
     *
     * <p>Part of a box having gone out makes its weight and its bulk flag
     * history: an invoice priced 4.100g out of 92.500g, and moving either figure
     * now would leave the remaining weight describing a box that never existed.
     * Everything else on the piece stays editable.
     */
    private static void requireWeightStillOpen(InventoryItem item, InventoryItemRequest request) {
        if (!item.isPartlySold()) {
            return;
        }
        if (item.getWeightGrams().compareTo(request.weightGrams()) != 0) {
            throw new StateConflictException(ErrorCode.ITEM_ALREADY_SOLD, "weightGrams",
                    ("Part of bulk item %s has been sold (%sg of %sg left), so its weight can no longer "
                            + "be changed.").formatted(item.getSerialNumber(),
                            item.getRemainingWeightGrams(), item.getWeightGrams()));
        }
        if (Boolean.TRUE.equals(request.bulk()) != item.isBulk()) {
            throw new StateConflictException(ErrorCode.ITEM_ALREADY_SOLD, "bulk",
                    "Part of bulk item %s has been sold, so it can no longer be changed to a single piece."
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
     * <p>Accepts "1" from a keyboard and stores "001", so the same physical
     * piece cannot be entered twice under two spellings of the same number.
     * A number already wider than the padding is left as it is: 905351 is
     * itself, not something longer.
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
