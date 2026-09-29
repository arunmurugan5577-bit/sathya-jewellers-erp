package com.jewellery.erp.sales.service;

import com.jewellery.erp.common.config.BusinessClock;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.exception.StateConflictException;
import com.jewellery.erp.common.util.Money;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.customer.entity.Customer;
import com.jewellery.erp.customer.service.CustomerService;
import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.inventory.service.InventoryItemService;
import com.jewellery.erp.numbering.DocumentNumberService;
import com.jewellery.erp.numbering.DocumentSeries;
import com.jewellery.erp.oldmetal.entity.OldMetalTransaction;
import com.jewellery.erp.oldmetal.service.OldMetalService;
import com.jewellery.erp.sales.calculation.SaleCalculator;
import com.jewellery.erp.sales.dto.SaleDtos;
import com.jewellery.erp.sales.dto.SaleRequests;
import com.jewellery.erp.sales.entity.AdjustmentStatus;
import com.jewellery.erp.sales.entity.PaymentStatus;
import com.jewellery.erp.sales.entity.Sale;
import com.jewellery.erp.sales.entity.SaleItem;
import com.jewellery.erp.sales.entity.SaleItemStatus;
import com.jewellery.erp.sales.entity.SaleOldMetalAdjustment;
import com.jewellery.erp.sales.entity.SalePayment;
import com.jewellery.erp.sales.entity.SaleStatus;
import com.jewellery.erp.sales.mapper.SaleMapper;
import com.jewellery.erp.sales.repository.SaleRepository;
import com.jewellery.erp.security.SecurityUtils;
import com.jewellery.erp.shop.service.SellerSnapshot;
import com.jewellery.erp.shop.service.ShopSettingsService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales invoices.
 *
 * <p>Completing a sale is one transaction that takes its locks in a fixed order -
 * inventory pieces, then old metal bills, then the invoice counter - so two
 * counters can never deadlock against each other, and so the gap-free invoice
 * number is held for the shortest possible time. Cancellation locks the invoice
 * first; nothing that creates a sale ever locks an existing invoice, so the two
 * orders cannot cross.
 */
@Service
@Transactional(readOnly = true)
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final SaleRepository repository;
    private final SaleMapper mapper;
    private final SaleCalculator calculator;
    private final CustomerService customerService;
    private final InventoryItemService inventoryItemService;
    private final OldMetalService oldMetalService;
    private final ShopSettingsService shopSettingsService;
    private final DocumentNumberService documentNumberService;
    private final BusinessClock businessClock;

    public SaleService(
            SaleRepository repository,
            SaleMapper mapper,
            SaleCalculator calculator,
            CustomerService customerService,
            InventoryItemService inventoryItemService,
            OldMetalService oldMetalService,
            ShopSettingsService shopSettingsService,
            DocumentNumberService documentNumberService,
            BusinessClock businessClock) {
        this.repository = repository;
        this.mapper = mapper;
        this.calculator = calculator;
        this.customerService = customerService;
        this.inventoryItemService = inventoryItemService;
        this.oldMetalService = oldMetalService;
        this.shopSettingsService = shopSettingsService;
        this.documentNumberService = documentNumberService;
        this.businessClock = businessClock;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<SaleDtos.Summary> findAll(
            String search, SaleStatus status, PaymentStatus paymentStatus, Long customerId,
            LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.from(repository.findAll(
                matching(search, status, paymentStatus, customerId, from, to), pageable), mapper::toSummary);
    }

    public SaleDtos.Detail findById(Long id) {
        return mapper.toDetail(requireDetailed(id));
    }

    /** Category, sub category, HSN and weight for the serial number typed at the counter. */
    public SaleDtos.ItemLookup lookupItem(String serialNumber) {
        return mapper.toLookup(inventoryItemService.requireSellable(serialNumber));
    }

    /**
     * Prices an invoice without saving it. Takes no locks and allocates no number,
     * so the form can call it on every keystroke; {@link #create} repeats every
     * check under lock.
     */
    public SaleDtos.Calculation calculate(SaleRequests.Sale request) {
        List<InventoryItem> pieces = new ArrayList<>();
        for (SaleRequests.Item line : request.items()) {
            pieces.add(inventoryItemService.requireSellable(line.serialNumber()));
        }
        assertNoDuplicates(pieces);

        BigDecimal adjustment = sumAdjustments(request);
        if (adjustment.signum() > 0) {
            if (request.customerId() == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "customerId",
                        "Choose the customer before applying old gold / silver.");
            }
            Map<Long, OldMetalTransaction> bills = oldMetalService.findUsableById(adjustmentIds(request));
            assertAdjustmentsValid(request, bills, request.customerId());
        }

        SaleCalculator.Result result = calculator.calculate(
                toInputs(request, pieces), request.discountAmount(), adjustment, sumPayments(request));

        List<SaleDtos.Line> lines = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            lines.add(mapper.toPreviewLine(i + 1, pieces.get(i),
                    particulars(request.items().get(i), pieces.get(i)), result.lines().get(i)));
        }
        return new SaleDtos.Calculation(lines, mapper.toTotals(result));
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public SaleDtos.Detail create(SaleRequests.Sale request) {
        if (request.customerId() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "customerId", "Customer is required.");
        }
        Customer customer = customerService.requireActive(request.customerId());
        LocalDate invoiceDate = resolveDate(request.invoiceDate());

        // Lock 1: the pieces. A second counter selling the same piece waits here,
        // then finds it SOLD.
        List<String> serials = request.items().stream()
                .map(line -> InventoryItemService.canonicalSerial(line.serialNumber()))
                .toList();
        if (new HashSet<>(serials).size() != serials.size()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "items",
                    "The same serial number is on this invoice more than once.");
        }
        Map<String, InventoryItem> locked = inventoryItemService.lockSellable(serials);
        List<InventoryItem> pieces = serials.stream().map(locked::get).toList();

        // Lock 2: the old gold / silver bills, checked against their locked balance.
        Map<Long, OldMetalTransaction> bills = request.adjustmentsOrEmpty().isEmpty()
                ? Map.of()
                : oldMetalService.lockUsable(adjustmentIds(request));
        assertAdjustmentsValid(request, bills, customer.getId());

        SaleCalculator.Result result = calculator.calculate(
                toInputs(request, pieces), request.discountAmount(), sumAdjustments(request), sumPayments(request));

        Sale sale = new Sale();
        sale.setCustomer(customer);
        sale.setInvoiceDate(invoiceDate);
        sale.setStatus(SaleStatus.COMPLETED);
        snapshotParties(sale, customer);
        applyTotals(sale, result);
        sale.setRemarks(StringNormalizer.trimToNull(request.remarks()));

        for (int i = 0; i < pieces.size(); i++) {
            sale.addItem(buildLine(i + 1, pieces.get(i), request.items().get(i), result.lines().get(i)));
        }
        for (SaleRequests.OldMetalAdjustment requested : request.adjustmentsOrEmpty()) {
            OldMetalTransaction bill = bills.get(requested.transactionId());
            BigDecimal amount = Money.money(requested.amount());
            bill.applyUsage(amount);

            SaleOldMetalAdjustment adjustment = new SaleOldMetalAdjustment();
            adjustment.setOldMetalTransaction(bill);
            adjustment.setAdjustmentAmount(amount);
            adjustment.setStatus(AdjustmentStatus.ACTIVE);
            sale.addAdjustment(adjustment);
        }
        for (SaleRequests.Payment requested : request.paymentsOrEmpty()) {
            sale.addPayment(buildPayment(requested, invoiceDate));
        }
        inventoryItemService.markSold(pieces);

        // Lock 3, last: the invoice counter.
        sale.setInvoiceNumber(documentNumberService.next(DocumentSeries.SALE_INVOICE, invoiceDate));

        Sale saved = repository.saveAndFlush(sale);
        log.info("Sale {} completed for customer {}: {} item(s), grand total {}, payable {}",
                saved.getInvoiceNumber(), customer.getCustomerCode(), pieces.size(),
                saved.getGrandTotal(), saved.getNetPayable());
        return mapper.toDetail(requireDetailed(saved.getId()));
    }

    /** Records money received later against an outstanding balance. */
    @Transactional
    public SaleDtos.Detail addPayment(Long id, SaleRequests.Payment request) {
        Sale sale = lock(id);
        if (sale.isCancelled()) {
            throw new StateConflictException(ErrorCode.SALE_ALREADY_CANCELLED,
                    "Invoice %s is cancelled; payments cannot be added.".formatted(sale.getInvoiceNumber()));
        }
        BigDecimal amount = Money.money(request.amount());
        if (amount.compareTo(sale.getBalanceAmount()) > 0) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_EXCEEDS_BALANCE, "amount",
                    "Payment of %s is more than the balance of %s on invoice %s."
                            .formatted(amount, sale.getBalanceAmount(), sale.getInvoiceNumber()));
        }
        sale.addPayment(buildPayment(request, businessClock.today()));
        BigDecimal paid = sale.getAmountPaid().add(amount);
        sale.setAmountPaid(paid);
        sale.setBalanceAmount(sale.getNetPayable().subtract(paid));
        sale.setPaymentStatus(calculator.paymentStatus(sale.getNetPayable(), paid));
        repository.flush();
        log.info("Payment of {} recorded on invoice {}", amount, sale.getInvoiceNumber());
        return mapper.toDetail(requireDetailed(id));
    }

    @Transactional
    public SaleDtos.Detail updateRemarks(Long id, String remarks) {
        Sale sale = lock(id);
        sale.setRemarks(StringNormalizer.trimToNull(remarks));
        repository.flush();
        return mapper.toDetail(requireDetailed(id));
    }

    /**
     * Cancels an invoice: the pieces go back on sale and old gold / silver value
     * goes back to its bills. The invoice itself, its number and its payments are
     * kept - a cancelled invoice is part of the GST record. Any refund of money
     * already paid is handled outside the system for now.
     */
    @Transactional
    public SaleDtos.Detail cancel(Long id, String reason) {
        Sale sale = lock(id);
        if (sale.isCancelled()) {
            throw new StateConflictException(ErrorCode.SALE_ALREADY_CANCELLED,
                    "Invoice %s is already cancelled.".formatted(sale.getInvoiceNumber()));
        }

        List<Long> inventoryIds = new ArrayList<>();
        for (SaleItem line : sale.getItems()) {
            if (line.getLineStatus() == SaleItemStatus.ACTIVE) {
                line.setLineStatus(SaleItemStatus.CANCELLED);
                inventoryIds.add(line.getInventoryItem().getId());
            }
        }
        Map<Long, BigDecimal> released = new LinkedHashMap<>();
        for (SaleOldMetalAdjustment adjustment : sale.getOldMetalAdjustments()) {
            if (adjustment.getStatus() == AdjustmentStatus.ACTIVE) {
                adjustment.setStatus(AdjustmentStatus.REVERSED);
                released.merge(adjustment.getOldMetalTransaction().getId(),
                        adjustment.getAdjustmentAmount(), BigDecimal::add);
            }
        }

        if (!inventoryIds.isEmpty()) {
            inventoryItemService.returnToStock(inventoryIds);
        }
        if (!released.isEmpty()) {
            oldMetalService.releaseUsage(released);
        }

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelReason(StringNormalizer.trimToNull(reason));
        sale.setCancelledBy(SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
        sale.setCancelledAt(Instant.now());
        repository.flush();
        log.info("Sale {} cancelled: {} piece(s) returned to stock", sale.getInvoiceNumber(), inventoryIds.size());
        return mapper.toDetail(requireDetailed(id));
    }

    // ------------------------------------------------------------- helpers ---

    private void assertAdjustmentsValid(
            SaleRequests.Sale request, Map<Long, OldMetalTransaction> bills, Long customerId) {
        Set<Long> seen = new HashSet<>();
        for (SaleRequests.OldMetalAdjustment requested : request.adjustmentsOrEmpty()) {
            if (!seen.add(requested.transactionId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "oldMetalAdjustments",
                        "The same purchase bill is applied more than once.");
            }
            OldMetalTransaction bill = bills.get(requested.transactionId());
            if (!bill.getCustomer().getId().equals(customerId)) {
                throw new BusinessRuleException(ErrorCode.OLD_METAL_CUSTOMER_MISMATCH, "oldMetalAdjustments",
                        "Purchase bill %s belongs to a different customer.".formatted(bill.getTransactionNumber()));
            }
            BigDecimal amount = Money.money(requested.amount());
            if (amount.compareTo(bill.availableAmount()) > 0) {
                throw new StateConflictException(ErrorCode.OLD_METAL_AMOUNT_EXCEEDED, "oldMetalAdjustments",
                        "Only %s is left on purchase bill %s; %s was requested."
                                .formatted(bill.availableAmount(), bill.getTransactionNumber(), amount));
            }
        }
    }

    private static void assertNoDuplicates(List<InventoryItem> pieces) {
        if (new HashSet<>(pieces.stream().map(InventoryItem::getId).toList()).size() != pieces.size()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "items",
                    "The same serial number is on this invoice more than once.");
        }
    }

    private List<SaleCalculator.LineInput> toInputs(SaleRequests.Sale request, List<InventoryItem> pieces) {
        List<SaleCalculator.LineInput> inputs = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            SaleRequests.Item line = request.items().get(i);
            InventoryItem piece = pieces.get(i);
            inputs.add(new SaleCalculator.LineInput(
                    piece.getWeightGrams(),
                    line.wastagePercentage(),
                    line.ratePerGram(),
                    line.makingCharge(),
                    piece.getHsnCode().getGstPercentage()));
        }
        return inputs;
    }

    private SaleItem buildLine(
            int lineNumber, InventoryItem piece, SaleRequests.Item requested, SaleCalculator.LineResult r) {
        SaleItem line = new SaleItem();
        line.setLineNumber((short) lineNumber);
        line.setInventoryItem(piece);
        line.setLineStatus(SaleItemStatus.ACTIVE);
        line.setSerialNumber(piece.getSerialNumber());
        line.setParticulars(particulars(requested, piece));
        line.setHsnCode(piece.getHsnCode().getHsnCode());
        line.setGstPercentage(r.gstPercentage());
        line.setItemType(piece.getItemType());
        line.setPurity(piece.getPurity());
        line.setCategory(piece.getCategory());
        line.setSubCategory(piece.getSubCategory());
        line.setNetWeightGrams(r.netWeightGrams());
        line.setWastagePercentage(r.wastagePercentage());
        line.setWastageWeightGrams(r.wastageWeightGrams());
        line.setGrossWeightGrams(r.grossWeightGrams());
        line.setRatePerGram(r.ratePerGram());
        line.setMakingCharge(r.makingCharge());
        line.setAmount(r.amount());
        line.setCgstAmount(r.cgstAmount());
        line.setSgstAmount(r.sgstAmount());
        line.setDiscountAmount(r.discountAmount());
        return line;
    }

    private SalePayment buildPayment(SaleRequests.Payment requested, LocalDate date) {
        SalePayment payment = new SalePayment();
        payment.setPaymentMethod(requested.method());
        payment.setAmount(Money.money(requested.amount()));
        payment.setReferenceNumber(StringNormalizer.trimToNull(requested.referenceNumber()));
        payment.setRemarks(StringNormalizer.trimToNull(requested.remarks()));
        payment.setPaymentDate(date);
        return payment;
    }

    private void snapshotParties(Sale sale, Customer customer) {
        sale.setCustomerName(customer.getFullName());
        sale.setCustomerMobile(customer.getMobileNumber());
        sale.setCustomerAddress(customer.formattedAddress());
        sale.setCustomerGstin(customer.getGstin());
        SellerSnapshot seller = SellerSnapshot.of(shopSettingsService.find());
        sale.setSellerName(seller.name());
        sale.setSellerAddress(seller.address());
        sale.setSellerMobile(seller.mobile());
        sale.setSellerGstin(seller.gstin());
    }

    private static void applyTotals(Sale sale, SaleCalculator.Result r) {
        sale.setSubtotal(r.subtotal());
        sale.setCgstAmount(r.cgstAmount());
        sale.setSgstAmount(r.sgstAmount());
        sale.setTaxAmount(r.taxAmount());
        sale.setDiscountAmount(r.discountAmount());
        sale.setGrandTotal(r.grandTotal());
        sale.setOldMetalAdjustmentAmount(r.oldMetalAdjustmentAmount());
        sale.setRoundOffAmount(r.roundOffAmount());
        sale.setNetPayable(r.netPayable());
        sale.setAmountPaid(r.amountPaid());
        sale.setBalanceAmount(r.balanceAmount());
        sale.setPaymentStatus(r.paymentStatus());
    }

    private static String particulars(SaleRequests.Item requested, InventoryItem piece) {
        String typed = StringNormalizer.trimToNull(requested.particulars());
        return typed != null ? typed : SaleMapper.defaultParticulars(piece);
    }

    private static List<Long> adjustmentIds(SaleRequests.Sale request) {
        return request.adjustmentsOrEmpty().stream().map(SaleRequests.OldMetalAdjustment::transactionId).toList();
    }

    private static BigDecimal sumAdjustments(SaleRequests.Sale request) {
        return request.adjustmentsOrEmpty().stream()
                .map(a -> Money.money(a.amount()))
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumPayments(SaleRequests.Sale request) {
        return request.paymentsOrEmpty().stream()
                .map(p -> Money.money(p.amount()))
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private LocalDate resolveDate(LocalDate requested) {
        LocalDate today = businessClock.today();
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "invoiceDate",
                    "The invoice date cannot be in the future.");
        }
        return requested;
    }

    private Sale requireDetailed(Long id) {
        return repository.findDetailedById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.INVOICE_NOT_FOUND, "Sale", id));
    }

    private Sale lock(Long id) {
        return repository.lockById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.INVOICE_NOT_FOUND, "Sale", id));
    }

    private Specification<Sale> matching(
            String search, SaleStatus status, PaymentStatus paymentStatus, Long customerId,
            LocalDate from, LocalDate to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String term = StringNormalizer.trimToNull(search);
            if (term != null) {
                String pattern = "%" + term.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("invoiceNumber")), pattern),
                        builder.like(builder.lower(root.get("customerName")), pattern),
                        builder.like(root.get("customerMobile"), "%" + term + "%")));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (paymentStatus != null) {
                predicates.add(builder.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customer").get("id"), customerId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("invoiceDate"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("invoiceDate"), to));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
