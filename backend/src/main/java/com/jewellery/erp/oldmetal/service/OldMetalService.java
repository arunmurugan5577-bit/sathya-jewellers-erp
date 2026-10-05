package com.jewellery.erp.oldmetal.service;

import com.jewellery.erp.common.config.BusinessClock;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.exception.StateConflictException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.customer.entity.Customer;
import com.jewellery.erp.customer.service.CustomerService;
import com.jewellery.erp.itemtype.entity.ItemType;
import com.jewellery.erp.itemtype.service.ItemTypeService;
import com.jewellery.erp.numbering.DocumentNumberService;
import com.jewellery.erp.numbering.DocumentSeries;
import com.jewellery.erp.oldmetal.dto.OldMetalDtos;
import com.jewellery.erp.oldmetal.dto.OldMetalItemRequest;
import com.jewellery.erp.oldmetal.dto.OldMetalTransactionRequest;
import com.jewellery.erp.oldmetal.entity.OldMetalStatus;
import com.jewellery.erp.oldmetal.entity.OldMetalTransaction;
import com.jewellery.erp.oldmetal.entity.OldMetalTransactionItem;
import com.jewellery.erp.oldmetal.mapper.OldMetalMapper;
import com.jewellery.erp.oldmetal.repository.OldMetalTransactionRepository;
import com.jewellery.erp.purity.service.PurityService;
import com.jewellery.erp.security.SecurityUtils;
import com.jewellery.erp.shop.service.SellerSnapshot;
import com.jewellery.erp.shop.service.ShopSettingsService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Old gold / silver bought from customers.
 *
 * <p>A purchase bill is a financial document in its own right: it is numbered,
 * printed, signed by the customer, and never deleted. Its value is spent by sales
 * through {@link #lockUsable} and {@link OldMetalTransaction#applyUsage}; this
 * service guarantees a bill's value can never be spent twice.
 */
@Service
@Transactional(readOnly = true)
public class OldMetalService {

    private static final Logger log = LoggerFactory.getLogger(OldMetalService.class);

    private final OldMetalTransactionRepository repository;
    private final OldMetalMapper mapper;
    private final OldMetalCalculator calculator;
    private final CustomerService customerService;
    private final ItemTypeService itemTypeService;
    private final PurityService purityService;
    private final ShopSettingsService shopSettingsService;
    private final DocumentNumberService documentNumberService;
    private final BusinessClock businessClock;
    private final Set<String> allowedItemTypeCodes;

    public OldMetalService(
            OldMetalTransactionRepository repository,
            OldMetalMapper mapper,
            OldMetalCalculator calculator,
            CustomerService customerService,
            ItemTypeService itemTypeService,
            PurityService purityService,
            ShopSettingsService shopSettingsService,
            DocumentNumberService documentNumberService,
            BusinessClock businessClock,
            @Value("${app.old-metal.item-type-codes:GOLD,SILV}") String[] allowedItemTypeCodes) {
        this.repository = repository;
        this.mapper = mapper;
        this.calculator = calculator;
        this.customerService = customerService;
        this.itemTypeService = itemTypeService;
        this.purityService = purityService;
        this.shopSettingsService = shopSettingsService;
        this.documentNumberService = documentNumberService;
        this.businessClock = businessClock;
        this.allowedItemTypeCodes = Arrays.stream(allowedItemTypeCodes)
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<OldMetalDtos.Summary> findAll(
            String search, OldMetalStatus status, Long customerId, LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.from(
                repository.findAll(matching(search, status, customerId, from, to), pageable), mapper::toSummary);
    }

    public OldMetalDtos.Detail findById(Long id) {
        return mapper.toDetail(requireDetailed(id));
    }

    /** Bills this customer can still apply to a sale, with their remaining value. */
    public List<OldMetalDtos.Option> findUsableForCustomer(Long customerId) {
        return repository.findUsableForCustomer(customerId, OldMetalStatus.USABLE).stream()
                .map(mapper::toOption)
                .toList();
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public OldMetalDtos.Detail create(OldMetalTransactionRequest request) {
        Customer customer = customerService.requireActive(request.customerId());
        LocalDate date = resolveDate(request.transactionDate());
        SellerSnapshot seller = SellerSnapshot.of(shopSettingsService.find());

        OldMetalTransaction transaction = new OldMetalTransaction();
        transaction.setCustomer(customer);
        transaction.setTransactionDate(date);
        transaction.setCustomerName(customer.getFullName());
        transaction.setCustomerMobile(customer.getMobileNumber());
        transaction.setCustomerAddress(customer.formattedAddress());
        transaction.setSellerName(seller.name());
        transaction.setSellerAddress(seller.address());
        transaction.setSellerMobile(seller.mobile());
        transaction.setSellerGstin(seller.gstin());
        transaction.setRemarks(StringNormalizer.trimToNull(request.remarks()));

        BigDecimal total = BigDecimal.ZERO;
        short lineNumber = 1;
        for (OldMetalItemRequest line : request.items()) {
            OldMetalTransactionItem item = buildItem(line, lineNumber++);
            transaction.addItem(item);
            total = total.add(item.getAmount());
        }
        if (total.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "items",
                    "The purchase bill total must be greater than zero.");
        }
        transaction.setTotalAmount(total);
        transaction.setUsedAmount(BigDecimal.ZERO);
        transaction.setStatus(OldMetalStatus.AVAILABLE);

        // Last, so the counter row is locked for as short a time as possible.
        transaction.setTransactionNumber(documentNumberService.next(DocumentSeries.OLD_METAL_PURCHASE, date));

        OldMetalTransaction saved = repository.save(transaction);
        log.info("Old metal purchase {} recorded for customer {}: {}",
                saved.getTransactionNumber(), customer.getCustomerCode(), total);
        return mapper.toDetail(requireDetailed(saved.getId()));
    }

    @Transactional
    public OldMetalDtos.Detail updateRemarks(Long id, String remarks) {
        OldMetalTransaction transaction = requireDetailed(id);
        transaction.setRemarks(StringNormalizer.trimToNull(remarks));
        return mapper.toDetail(transaction);
    }

    /** A bill may be cancelled only while none of its value has been spent. */
    @Transactional
    public OldMetalDtos.Detail cancel(Long id, String reason) {
        OldMetalTransaction transaction = lockOne(id);
        if (transaction.getStatus() == OldMetalStatus.CANCELLED) {
            throw new StateConflictException(ErrorCode.OLD_METAL_ALREADY_USED,
                    "Purchase bill %s is already cancelled.".formatted(transaction.getTransactionNumber()));
        }
        if (transaction.getUsedAmount().signum() > 0) {
            throw new StateConflictException(ErrorCode.OLD_METAL_ALREADY_USED,
                    "Purchase bill %s has already been applied to a sale and cannot be cancelled. "
                            .formatted(transaction.getTransactionNumber())
                            + "Cancel that sale first.");
        }
        transaction.cancel(StringNormalizer.trimToNull(reason), currentUser(), Instant.now());
        log.info("Old metal purchase {} cancelled", transaction.getTransactionNumber());
        return mapper.toDetail(requireDetailed(id));
    }

    // ------------------------------------------------------------ for sales ---

    /**
     * Locks the bills a sale wants to use, for the rest of the sale's transaction.
     *
     * <p>{@code MANDATORY} because a lock taken in its own transaction would be
     * released before the sale commits - exactly the window a double-spend needs.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, OldMetalTransaction> lockUsable(Collection<Long> ids) {
        Map<Long, OldMetalTransaction> locked = repository.lockAllById(ids).stream()
                .collect(Collectors.toMap(OldMetalTransaction::getId, Function.identity()));
        for (Long id : ids) {
            OldMetalTransaction transaction = locked.get(id);
            if (transaction == null) {
                throw ResourceNotFoundException.of(ErrorCode.OLD_METAL_NOT_FOUND, "Old gold/silver purchase", id);
            }
            if (!OldMetalStatus.USABLE.contains(transaction.getStatus())) {
                throw new StateConflictException(ErrorCode.OLD_METAL_ALREADY_USED, "oldMetalAdjustments",
                        "Purchase bill %s is %s and has no value left to use."
                                .formatted(transaction.getTransactionNumber(),
                                        transaction.getStatus().name().toLowerCase().replace('_', ' ')));
            }
        }
        return locked;
    }

    /** The same checks as {@link #lockUsable}, without locking - for the sale preview. */
    public Map<Long, OldMetalTransaction> findUsableById(Collection<Long> ids) {
        Map<Long, OldMetalTransaction> found = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(OldMetalTransaction::getId, Function.identity()));
        for (Long id : ids) {
            OldMetalTransaction transaction = found.get(id);
            if (transaction == null) {
                throw ResourceNotFoundException.of(ErrorCode.OLD_METAL_NOT_FOUND, "Old gold/silver purchase", id);
            }
            if (!OldMetalStatus.USABLE.contains(transaction.getStatus())) {
                throw new StateConflictException(ErrorCode.OLD_METAL_ALREADY_USED, "oldMetalAdjustments",
                        "Purchase bill %s has no value left to use.".formatted(transaction.getTransactionNumber()));
            }
        }
        return found;
    }

    /** Gives value back to bills whose sale has been cancelled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseUsage(Map<Long, BigDecimal> amountsByTransactionId) {
        Map<Long, OldMetalTransaction> locked = repository.lockAllById(amountsByTransactionId.keySet()).stream()
                .collect(Collectors.toMap(OldMetalTransaction::getId, Function.identity()));
        amountsByTransactionId.forEach((id, amount) -> locked.get(id).reverseUsage(amount));
    }

    // ------------------------------------------------------------- helpers ---

    private OldMetalTransactionItem buildItem(OldMetalItemRequest line, short lineNumber) {
        ItemType itemType = itemTypeService.requireActive(line.itemTypeId());
        if (!allowedItemTypeCodes.contains(itemType.getCode())) {
            throw new BusinessRuleException(ErrorCode.OLD_METAL_INVALID_ITEM_TYPE, "itemTypeId",
                    "%s cannot be bought as old metal. Allowed: %s."
                            .formatted(itemType.getName(), String.join(", ", allowedItemTypeCodes)));
        }
        if (line.grossWeightGrams() != null && line.grossWeightGrams().compareTo(line.netWeightGrams()) < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "grossWeightGrams",
                    "Gross weight cannot be less than net weight.");
        }

        OldMetalTransactionItem item = new OldMetalTransactionItem();
        item.setLineNumber(lineNumber);
        item.setItemType(itemType);
        item.setPurity(line.purityId() == null
                ? null
                : purityService.requireActiveForItemType(line.purityId(), itemType.getId()));
        item.setParticulars(StringNormalizer.normalizeName(line.particulars()));
        item.setHsnCode(line.hsnCode().trim());
        item.setNetWeightGrams(line.netWeightGrams());
        item.setGrossWeightGrams(line.grossWeightGrams());
        item.setRatePerGram(line.ratePerGram());
        item.setAmount(calculator.lineAmount(
                line.grossWeightGrams(), line.netWeightGrams(), line.ratePerGram()));
        return item;
    }

    private LocalDate resolveDate(LocalDate requested) {
        LocalDate today = businessClock.today();
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "transactionDate",
                    "The purchase date cannot be in the future.");
        }
        return requested;
    }

    private OldMetalTransaction requireDetailed(Long id) {
        return repository.findDetailedById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.OLD_METAL_NOT_FOUND,
                        "Old gold/silver purchase", id));
    }

    private OldMetalTransaction lockOne(Long id) {
        return repository.lockAllById(List.of(id)).stream()
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of(ErrorCode.OLD_METAL_NOT_FOUND,
                        "Old gold/silver purchase", id));
    }

    private static String currentUser() {
        return SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER);
    }

    private Specification<OldMetalTransaction> matching(
            String search, OldMetalStatus status, Long customerId, LocalDate from, LocalDate to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String term = StringNormalizer.trimToNull(search);
            if (term != null) {
                String pattern = "%" + term.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("transactionNumber")), pattern),
                        builder.like(builder.lower(root.get("customerName")), pattern),
                        builder.like(root.get("customerMobile"), "%" + term + "%")));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customer").get("id"), customerId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("transactionDate"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("transactionDate"), to));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
