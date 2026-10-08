package com.jewellery.erp.wholesale.service;

import com.jewellery.erp.common.config.BusinessClock;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.exception.StateConflictException;
import com.jewellery.erp.common.util.AmountInWords;
import com.jewellery.erp.common.util.Money;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.customer.entity.Customer;
import com.jewellery.erp.customer.service.CustomerService;
import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.inventory.service.InventoryItemService;
import com.jewellery.erp.numbering.DocumentNumberService;
import com.jewellery.erp.numbering.DocumentSeries;
import com.jewellery.erp.security.SecurityUtils;
import com.jewellery.erp.shop.service.ShopSettingsService;
import com.jewellery.erp.wholesale.dto.WholesaleDtos;
import com.jewellery.erp.wholesale.entity.WholesaleBalance;
import com.jewellery.erp.wholesale.entity.WholesaleEstimate;
import com.jewellery.erp.wholesale.entity.WholesaleEstimateItem;
import com.jewellery.erp.wholesale.entity.WholesaleItemStatus;
import com.jewellery.erp.wholesale.entity.WholesaleStatus;
import com.jewellery.erp.wholesale.mapper.WholesaleMapper;
import com.jewellery.erp.wholesale.repository.WholesaleBalanceRepository;
import com.jewellery.erp.wholesale.repository.WholesaleEstimateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wholesale selling: gold by pure weight, against a running account.
 *
 * <p>Saving an estimate takes its locks in the same fixed order a sale does -
 * the pieces, then the party's balance, then the number counter - so the two
 * can never deadlock against each other and the gap-free number is held for the
 * shortest time. Cancellation locks the estimate first; nothing that creates
 * one ever locks an existing estimate.
 *
 * <p>The party balance is read under lock and written in the same transaction.
 * Two estimates raised for one party at the same moment would otherwise both
 * see the same opening figures, and the second would quietly overwrite the
 * first's closing ones.
 */
@Service
public class WholesaleService {

    private static final Logger log = LoggerFactory.getLogger(WholesaleService.class);

    private final WholesaleEstimateRepository repository;
    private final WholesaleBalanceRepository balanceRepository;
    private final WholesaleCalculator calculator;
    private final WholesaleMapper mapper;
    private final CustomerService customerService;
    private final InventoryItemService inventoryItemService;
    private final DocumentNumberService documentNumberService;
    private final ShopSettingsService shopSettingsService;
    private final BusinessClock businessClock;

    public WholesaleService(
            WholesaleEstimateRepository repository,
            WholesaleBalanceRepository balanceRepository,
            WholesaleCalculator calculator,
            WholesaleMapper mapper,
            CustomerService customerService,
            InventoryItemService inventoryItemService,
            DocumentNumberService documentNumberService,
            ShopSettingsService shopSettingsService,
            BusinessClock businessClock) {
        this.repository = repository;
        this.balanceRepository = balanceRepository;
        this.calculator = calculator;
        this.mapper = mapper;
        this.customerService = customerService;
        this.inventoryItemService = inventoryItemService;
        this.documentNumberService = documentNumberService;
        this.shopSettingsService = shopSettingsService;
        this.businessClock = businessClock;
    }

    // ------------------------------------------------------------- lookup ---

    /** A piece in stock, with a touch suggested from its purity. */
    @Transactional(readOnly = true)
    public WholesaleDtos.ItemLookup lookupItem(String serialNumber) {
        InventoryItem item = inventoryItemService.requireSellable(serialNumber);
        return new WholesaleDtos.ItemLookup(
                item.getId(),
                item.getSerialNumber(),
                jewelNameOf(item),
                item.getItemType().getName(),
                item.getPurity().getName(),
                // What is still there, not what arrived: a bulk box half sold at
                // the counter has half a box left to send to a dealer.
                item.getRemainingWeightGrams(),
                suggestedTouch(item));
    }

    /** A party's account as it stands, optionally valued at a rate. */
    @Transactional(readOnly = true)
    public WholesaleDtos.Balance balanceFor(Long customerId, BigDecimal pureRatePerGram) {
        Customer customer = customerService.requireActive(customerId);
        WholesaleBalance balance = balanceRepository.findById(customerId).orElse(null);
        BigDecimal pure = balance == null ? BigDecimal.ZERO : balance.getPureGrams();
        BigDecimal misc = balance == null ? BigDecimal.ZERO : balance.getMiscAmount();
        BigDecimal value = pureRatePerGram == null
                ? null
                : calculator.balanceValue(pure, misc, pureRatePerGram);
        return new WholesaleDtos.Balance(
                customer.getId(), customer.getFullName(), Money.weight(pure), Money.money(misc), value);
    }

    // ---------------------------------------------------------- pricing ---

    /**
     * Prices an estimate without saving it. Takes no locks and allocates no
     * number, so the form can call it on every keystroke.
     */
    @Transactional(readOnly = true)
    public WholesaleDtos.Calculation calculate(WholesaleDtos.Request request) {
        List<InventoryItem> pieces = new ArrayList<>();
        for (WholesaleDtos.ItemRequest line : request.itemsOrEmpty()) {
            pieces.add(inventoryItemService.requireSellable(line.serialNumber()));
        }
        BigDecimal openingPure = BigDecimal.ZERO;
        BigDecimal openingMisc = BigDecimal.ZERO;
        if (request.customerId() != null) {
            WholesaleBalance balance = balanceRepository.findById(request.customerId()).orElse(null);
            if (balance != null) {
                openingPure = balance.getPureGrams();
                openingMisc = balance.getMiscAmount();
            }
        }
        return price(request, pieces, openingPure, openingMisc);
    }

    // ------------------------------------------------------------- create ---

    @Transactional
    public WholesaleDtos.Detail create(WholesaleDtos.Request request) {
        if (request.customerId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "customerId",
                    "Choose the wholesale party before saving.");
        }
        Customer customer = customerService.requireActive(request.customerId());
        LocalDate estimateDate = resolveDate(request.estimateDate());

        // Lock 1: the pieces. A second counter selling the same piece waits
        // here, then finds it SOLD.
        List<String> serials = request.itemsOrEmpty().stream()
                .map(line -> InventoryItemService.canonicalSerial(line.serialNumber()))
                .toList();
        if (new HashSet<>(serials).size() != serials.size()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "items",
                    "The same serial number is on this estimate more than once.");
        }
        Map<String, InventoryItem> locked = inventoryItemService.lockSellable(serials);
        List<InventoryItem> pieces = serials.stream().map(locked::get).toList();

        // Lock 2: the party's account, so the opening balance cannot move
        // between reading it and writing the closing one.
        WholesaleBalance balance = lockOrCreateBalance(customer.getId());
        WholesaleDtos.Calculation priced =
                price(request, pieces, balance.getPureGrams(), balance.getMiscAmount());

        WholesaleEstimate estimate = new WholesaleEstimate();
        estimate.setCustomer(customer);
        estimate.setEstimateDate(estimateDate);
        estimate.setStatus(WholesaleStatus.COMPLETED);
        estimate.setCustomerCode(customer.getCustomerCode());
        estimate.setCustomerName(customer.getFullName());
        estimate.setCustomerMobile(customer.getMobileNumber());
        estimate.setPureRatePerGram(Money.money(request.pureRatePerGram()));
        estimate.setRemarks(StringNormalizer.trimToNull(request.remarks()));
        applyTotals(estimate, priced.totals());

        for (int i = 0; i < pieces.size(); i++) {
            estimate.addItem(buildLine(priced.lines().get(i), pieces.get(i)));
        }

        // Each piece goes out whole, so what it gives is what it had left.
        List<InventoryItemService.Billed> billed = new ArrayList<>(pieces.size());
        for (InventoryItem piece : pieces) {
            billed.add(new InventoryItemService.Billed(piece, piece.getRemainingWeightGrams()));
        }
        inventoryItemService.billOut(billed);
        balance.add(priced.totals().totalPureGrams(), priced.totals().totalMiscAmount());
        balance.setUpdatedBy(SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));

        // Lock 3, last: the number counter.
        estimate.setEstimateNumber(documentNumberService.next(DocumentSeries.WHOLESALE_ESTIMATE, estimateDate));

        WholesaleEstimate saved = repository.saveAndFlush(estimate);
        log.info("Wholesale estimate {} for {}: {} piece(s), {} g pure, total {}",
                saved.getEstimateNumber(), customer.getCustomerCode(), pieces.size(),
                saved.getTotalPureGrams(), saved.getTotalAmount());
        return mapper.toDetail(requireDetailed(saved.getId()), shopSettingsService.find());
    }

    // ------------------------------------------------------------- cancel ---

    /**
     * Cancels an estimate: the pieces go back to stock and the party's account
     * is put back where it was.
     */
    @Transactional
    public WholesaleDtos.Detail cancel(Long id, String reason) {
        WholesaleEstimate estimate = repository.lockById(id).orElseThrow(() ->
                new ResourceNotFoundException("No wholesale estimate with id " + id + "."));
        if (estimate.isCancelled()) {
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    "Estimate %s is already cancelled.".formatted(estimate.getEstimateNumber()));
        }

        WholesaleEstimate detailed = requireDetailed(id);
        // The weight each line took, so a bulk box gets back exactly what it gave.
        Map<Long, BigDecimal> returning = new LinkedHashMap<>();
        for (WholesaleEstimateItem line : detailed.getItems()) {
            if (line.getLineStatus() == WholesaleItemStatus.ACTIVE) {
                line.setLineStatus(WholesaleItemStatus.CANCELLED);
                returning.merge(line.getInventoryItem().getId(), line.getJewelWeightGrams(), BigDecimal::add);
            }
        }
        if (!returning.isEmpty()) {
            inventoryItemService.returnToStock(returning);
        }

        // Reverse exactly what this estimate added, not a recomputation: the
        // rate may have moved since, and the account must come back to where
        // it was, not to where today's rate would put it.
        WholesaleBalance balance = lockOrCreateBalance(detailed.getCustomer().getId());
        balance.add(detailed.getTotalPureGrams().negate(), detailed.getTotalMiscAmount().negate());
        balance.setUpdatedBy(SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));

        detailed.setStatus(WholesaleStatus.CANCELLED);
        detailed.setCancelReason(StringNormalizer.trimToNull(reason));
        detailed.setCancelledBy(SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
        detailed.setCancelledAt(Instant.now());
        repository.flush();
        log.info("Wholesale estimate {} cancelled: {} piece(s) returned to stock, {} g pure reversed",
                detailed.getEstimateNumber(), returning.size(), detailed.getTotalPureGrams());
        return mapper.toDetail(requireDetailed(id), shopSettingsService.find());
    }

    // --------------------------------------------------------------- read ---

    @Transactional(readOnly = true)
    public WholesaleDtos.Detail findById(Long id) {
        return mapper.toDetail(requireDetailed(id), shopSettingsService.find());
    }

    @Transactional(readOnly = true)
    public PageResponse<WholesaleDtos.Summary> findAll(
            LocalDate from, LocalDate to, Long customerId, WholesaleStatus status, Pageable pageable) {
        Page<WholesaleEstimate> page = repository.findAll(
                WholesaleSpecifications.matching(from, to, customerId, status), pageable);
        return PageResponse.from(page, mapper::toSummary);
    }

    // ------------------------------------------------------------ helpers ---

    /**
     * Prices the lines and works the account forward from the opening figures.
     *
     * <p>Shared by {@link #calculate} and {@link #create} so the preview on the
     * form and the saved document can never disagree.
     */
    private WholesaleDtos.Calculation price(
            WholesaleDtos.Request request,
            List<InventoryItem> pieces,
            BigDecimal openingPure,
            BigDecimal openingMisc) {

        BigDecimal pureRate = Money.money(request.pureRatePerGram());
        List<WholesaleDtos.Line> lines = new ArrayList<>();
        BigDecimal totalPure = BigDecimal.ZERO;
        BigDecimal totalMisc = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        List<WholesaleDtos.ItemRequest> requested = request.itemsOrEmpty();
        for (int i = 0; i < requested.size(); i++) {
            WholesaleDtos.ItemRequest line = requested.get(i);
            InventoryItem piece = pieces.get(i);

            // A wholesale estimate moves the piece entire - there is no part-box
            // on this form, unlike the retail counter - so the line weighs
            // whatever is left of it. For a single article that is its own weight.
            BigDecimal jewelWeight = piece.getRemainingWeightGrams();
            BigDecimal touch = line.purePercentage();
            BigDecimal pureWeight = calculator.pureWeight(jewelWeight, touch);
            // The line may quote its own rate; most do not and take the day's.
            BigDecimal rate = line.ratePerGram() == null ? pureRate : Money.money(line.ratePerGram());
            BigDecimal making = line.makingCharge() == null ? BigDecimal.ZERO : Money.money(line.makingCharge());
            BigDecimal stone = line.stoneAmount() == null ? BigDecimal.ZERO : Money.money(line.stoneAmount());
            BigDecimal amount = calculator.itemAmount(pureWeight, rate, making, stone);

            lines.add(new WholesaleDtos.Line(
                    i + 1,
                    piece.getId(),
                    piece.getSerialNumber(),
                    jewelNameFor(line, piece),
                    jewelWeight,
                    Money.money(touch),
                    pureWeight,
                    rate,
                    making,
                    stone,
                    amount,
                    WholesaleItemStatus.ACTIVE.name()));

            totalPure = totalPure.add(pureWeight);
            totalMisc = totalMisc.add(calculator.miscAmount(making, stone));
            totalAmount = totalAmount.add(amount);
        }

        BigDecimal openPure = Money.weight(openingPure);
        BigDecimal openMisc = Money.money(openingMisc);
        BigDecimal closePure = Money.weight(openPure.add(totalPure));
        BigDecimal closeMisc = Money.money(openMisc.add(totalMisc));

        WholesaleDtos.Totals totals = new WholesaleDtos.Totals(
                openPure,
                openMisc,
                calculator.balanceValue(openPure, openMisc, pureRate),
                Money.weight(totalPure),
                Money.money(totalMisc),
                Money.rupees(totalAmount),
                closePure,
                closeMisc,
                calculator.balanceValue(closePure, closeMisc, pureRate),
                AmountInWords.rupees(Money.rupees(totalAmount)));
        return new WholesaleDtos.Calculation(lines, totals);
    }

    private WholesaleEstimateItem buildLine(WholesaleDtos.Line priced, InventoryItem piece) {
        WholesaleEstimateItem item = new WholesaleEstimateItem();
        item.setLineNumber(priced.lineNumber());
        item.setInventoryItem(piece);
        item.setSerialNumber(piece.getSerialNumber());
        item.setJewelName(priced.jewelName());
        item.setJewelWeightGrams(priced.jewelWeightGrams());
        item.setPurePercentage(priced.purePercentage());
        item.setPureWeightGrams(priced.pureWeightGrams());
        item.setRatePerGram(priced.ratePerGram());
        item.setMakingCharge(priced.makingCharge());
        item.setStoneAmount(priced.stoneAmount());
        item.setItemAmount(priced.itemAmount());
        item.setLineStatus(WholesaleItemStatus.ACTIVE);
        return item;
    }

    private static void applyTotals(WholesaleEstimate estimate, WholesaleDtos.Totals totals) {
        estimate.setOpeningPureGrams(totals.openingPureGrams());
        estimate.setOpeningMiscAmount(totals.openingMiscAmount());
        estimate.setOpeningValue(totals.openingValue());
        estimate.setTotalPureGrams(totals.totalPureGrams());
        estimate.setTotalMiscAmount(totals.totalMiscAmount());
        estimate.setTotalAmount(totals.totalAmount());
        estimate.setClosingPureGrams(totals.closingPureGrams());
        estimate.setClosingMiscAmount(totals.closingMiscAmount());
        estimate.setClosingValue(totals.closingValue());
    }

    private WholesaleBalance lockOrCreateBalance(Long customerId) {
        return balanceRepository.lockByCustomerId(customerId).orElseGet(() -> {
            WholesaleBalance fresh = new WholesaleBalance();
            fresh.setCustomerId(customerId);
            return balanceRepository.saveAndFlush(fresh);
        });
    }

    private LocalDate resolveDate(LocalDate requested) {
        LocalDate today = businessClock.today();
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "estimateDate",
                    "An estimate cannot be dated in the future.");
        }
        return requested;
    }

    private WholesaleEstimate requireDetailed(Long id) {
        return repository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No wholesale estimate with id " + id + "."));
    }

    private static String jewelNameFor(WholesaleDtos.ItemRequest line, InventoryItem piece) {
        String typed = StringNormalizer.trimToNull(line.jewelName());
        return typed == null ? jewelNameOf(piece) : typed;
    }

    /** What the piece is called on the estimate: the most specific name it has. */
    private static String jewelNameOf(InventoryItem item) {
        if (item.getSubCategory() != null) {
            return item.getSubCategory().getName();
        }
        return item.getCategory().getName();
    }

    /**
     * A starting touch for the form, read from the purity master.
     *
     * <p>916 is 91.6% gold by the hallmark, but the trade settles 916 ornaments
     * at a touch nearer 98 once the alloy is assayed, so this is a suggestion
     * the counter overwrites, never a figure the estimate is priced on.
     */
    private static BigDecimal suggestedTouch(InventoryItem item) {
        BigDecimal fineness = item.getPurity().getPurityValue();
        if (fineness == null) {
            return null;
        }
        // Fineness is held per mille (916, 999); the estimate wants a percentage.
        return Money.money(fineness.divide(BigDecimal.TEN, 3, java.math.RoundingMode.HALF_UP));
    }
}
