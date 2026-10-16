package com.jewellery.erp.chit.service;

import com.jewellery.erp.chit.dto.ChitDtos;
import com.jewellery.erp.chit.entity.ChitAccount;
import com.jewellery.erp.chit.entity.ChitPayment;
import com.jewellery.erp.chit.entity.ChitRedemption;
import com.jewellery.erp.chit.entity.RedemptionKind;
import com.jewellery.erp.chit.entity.ChitScheme;
import com.jewellery.erp.chit.entity.ChitSchemeType;
import com.jewellery.erp.chit.entity.ChitStatus;
import com.jewellery.erp.chit.mapper.ChitMapper;
import com.jewellery.erp.chit.repository.ChitAccountRepository;
import com.jewellery.erp.chit.repository.ChitSchemeRepository;
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
import com.jewellery.erp.numbering.DocumentNumberService;
import com.jewellery.erp.numbering.DocumentSeries;
import com.jewellery.erp.sales.entity.PaymentMethod;
import com.jewellery.erp.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chits: a customer saves monthly towards gold, silver or money.
 *
 * <p>Eleven instalments from the customer and a twelfth from the shop. The
 * bonus is written as a payment row like any other, so a balance is the sum of
 * the instalments and there is nothing to remember to add on - and on a gold
 * chit the bonus buys grams at a rate exactly as the other eleven did.
 *
 * <p>Maturing is its own step rather than something the eleventh payment does.
 * A gold bonus needs a rate somebody types in, and a counter taking a payment
 * should not be stopped to ask for one; a chit that has had all eleven sits at
 * COMPLETE until the shop adds the bonus.
 *
 * <p>Every write locks the chit first. Two counters taking the same month's
 * instalment is the obvious way to pay twice, and the unique index on
 * (chit, instalment) is the backstop under the lock.
 */
@Service
@Transactional(readOnly = true)
public class ChitService {

    private static final Logger log = LoggerFactory.getLogger(ChitService.class);

    private final ChitAccountRepository accountRepository;
    private final ChitSchemeRepository schemeRepository;
    private final ChitMapper mapper;
    private final ChitCalculator calculator;
    private final CustomerService customerService;
    private final DocumentNumberService documentNumberService;
    private final BusinessClock businessClock;

    public ChitService(
            ChitAccountRepository accountRepository,
            ChitSchemeRepository schemeRepository,
            ChitMapper mapper,
            ChitCalculator calculator,
            CustomerService customerService,
            DocumentNumberService documentNumberService,
            BusinessClock businessClock) {
        this.accountRepository = accountRepository;
        this.schemeRepository = schemeRepository;
        this.mapper = mapper;
        this.calculator = calculator;
        this.customerService = customerService;
        this.documentNumberService = documentNumberService;
        this.businessClock = businessClock;
    }

    // ------------------------------------------------------------- queries ---

    /** The schemes a customer can be offered, cheapest first. */
    public List<ChitDtos.Scheme> schemes() {
        return mapper.toSchemes(schemeRepository.findByActiveTrueOrderByMonthlyAmountAsc());
    }

    public PageResponse<ChitDtos.Summary> findAll(
            String search, ChitStatus status, ChitSchemeType schemeType, Long customerId, Pageable pageable) {
        return PageResponse.from(
                accountRepository.findAll(matching(search, status, schemeType, customerId), pageable),
                mapper::toSummary);
    }

    public ChitDtos.Account findById(Long id) {
        return mapper.toAccount(requireDetailed(id));
    }

    // ------------------------------------------------------------ commands ---

    /**
     * Starts a chit.
     *
     * <p>The scheme's terms are copied onto the chit rather than referenced, so
     * raising the 2,000 scheme for new joiners leaves everyone already in it
     * paying what they agreed to pay.
     */
    @Transactional
    public ChitDtos.Account open(ChitDtos.OpenRequest request) {
        Customer customer = customerService.requireActive(request.customerId());
        ChitScheme scheme = schemeRepository.findById(request.schemeId())
                .orElseThrow(() -> ResourceNotFoundException.of("Chit scheme", request.schemeId()));
        if (!scheme.isActive()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "schemeId",
                    "Scheme %s is no longer offered.".formatted(scheme.getName()));
        }
        LocalDate startedOn = resolveDate(request.startedOn(), "startedOn");

        ChitAccount account = new ChitAccount();
        account.setCustomer(customer);
        account.setScheme(scheme);
        account.setSchemeType(scheme.getSchemeType());
        account.setMonthlyAmount(scheme.getMonthlyAmount());
        account.setPayableInstalments(scheme.getPayableInstalments());
        account.setTotalInstalments(scheme.getTotalInstalments());
        account.setStartedOn(startedOn);
        account.setStatus(ChitStatus.ACTIVE);
        account.setRemarks(StringNormalizer.trimToNull(request.remarks()));
        account.setChitNumber(documentNumberService.next(DocumentSeries.CHIT_ACCOUNT, startedOn));

        ChitAccount saved = accountRepository.saveAndFlush(account);
        log.info("Chit {} opened for {} on {}: {} x {} instalments",
                saved.getChitNumber(), customer.getCustomerCode(), scheme.getName(),
                scheme.getMonthlyAmount(), scheme.getPayableInstalments());
        return mapper.toAccount(requireDetailed(saved.getId()));
    }

    /**
     * Takes one instalment.
     *
     * <p>The amount is the scheme's, never the caller's: a chit is a fixed
     * monthly sum and letting a request name its own would quietly let a
     * customer short-pay a month and still be counted as having paid it.
     *
     * <p>Instalments are numbered in order and the next is always the next
     * along. A month paid late is still the next instalment - the customer
     * simply finishes later, which is what the shop asked for.
     */
    @Transactional
    public ChitDtos.Account pay(Long id, ChitDtos.PaymentRequest request) {
        ChitAccount account = lock(id);
        if (account.getStatus() != ChitStatus.ACTIVE) {
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    "Chit %s is %s and takes no more instalments."
                            .formatted(account.getChitNumber(), account.getStatus().name().toLowerCase()));
        }
        if (account.getPaidInstalments() >= account.getPayableInstalments()) {
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    "Chit %s has had all %d instalments. Add the bonus to mature it."
                            .formatted(account.getChitNumber(), account.getPayableInstalments()));
        }
        LocalDate paidOn = resolveDate(request.paidOn(), "paidOn");

        ChitPayment payment = buildPayment(account, account.nextInstalmentNo(), paidOn,
                account.getMonthlyAmount(), request.ratePerGram(), false);
        payment.setPaymentMethod(parseMethod(request.paymentMethod()));
        payment.setReferenceNo(StringNormalizer.trimToNull(request.referenceNo()));
        payment.setRemarks(StringNormalizer.trimToNull(request.remarks()));

        account.applyPayment(payment);
        accountRepository.flush();
        log.info("Chit {}: instalment {} of {} taken, {}{}",
                account.getChitNumber(), payment.getInstalmentNo(), account.getPayableInstalments(),
                payment.getAmount(), payment.getGrams() == null ? "" : " buying " + payment.getGrams() + " g");
        return mapper.toAccount(requireDetailed(id));
    }

    /**
     * Puts in the shop's month and matures the chit.
     *
     * <p>Refused until every instalment the customer owes is in: the bonus is
     * for finishing, and crediting it earlier would hand over gold nobody has
     * paid for. On a gold chit the bonus buys grams at the rate given here -
     * the rate on the maturity day - the same way the other eleven did.
     */
    @Transactional
    public ChitDtos.Account mature(Long id, ChitDtos.MatureRequest request) {
        ChitAccount account = lock(id);
        if (account.getStatus() == ChitStatus.MATURED || account.getStatus() == ChitStatus.CLOSED) {
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    "Chit %s has already matured.".formatted(account.getChitNumber()));
        }
        if (account.getStatus() != ChitStatus.COMPLETE) {
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    ("Chit %s has had %d of its %d instalments. It matures once the last one is in.")
                            .formatted(account.getChitNumber(), account.getPaidInstalments(),
                                    account.getPayableInstalments()));
        }
        LocalDate maturedOn = resolveDate(request.maturedOn(), "maturedOn");

        // The bonus is however many months the shop puts in - one, as the shop
        // runs it, but read from the chit rather than assumed.
        short bonusMonths = (short) (account.getTotalInstalments() - account.getPayableInstalments());
        for (short i = 0; i < bonusMonths; i++) {
            ChitPayment bonus = buildPayment(account, account.nextInstalmentNo(), maturedOn,
                    account.getMonthlyAmount(), request.ratePerGram(), true);
            bonus.setRemarks("Bonus instalment, paid by the shop");
            account.applyPayment(bonus);
        }
        account.mature(maturedOn);
        accountRepository.flush();
        log.info("Chit {} matured: {} bonus month(s) added, balance {}{}",
                account.getChitNumber(), bonusMonths, account.getBalanceAmount(),
                account.isGold() ? " / " + account.getBalanceGrams() + " g" : "");
        return mapper.toAccount(requireDetailed(id));
    }

    /**
     * The customer takes the chit as cash.
     *
     * <p>The whole balance goes and the chit closes: a withdrawal is not a
     * part payment, it is the customer finishing with the chit. A gold chit is
     * valued at the rate given here, which is the point of the scheme - eleven
     * instalments bought gold at eleven rates and all of it is sold at one.
     *
     * <p>The money leaving the drawer is the counter's business; what is
     * recorded here is that the chit was paid out and for how much.
     */
    @Transactional
    public ChitDtos.Account withdraw(Long id, ChitDtos.WithdrawRequest request) {
        ChitAccount account = lock(id);
        requireRedeemable(account);
        LocalDate withdrawnOn = resolveDate(request.withdrawnOn(), "withdrawnOn");

        ChitRedemption redemption = new ChitRedemption();
        redemption.setRedeemedOn(withdrawnOn);
        redemption.setKind(RedemptionKind.CASH);
        redemption.setPaymentMethod(parseMethod(request.paymentMethod()));
        redemption.setReferenceNo(StringNormalizer.trimToNull(request.referenceNo()));
        redemption.setRemarks(StringNormalizer.trimToNull(request.remarks()));

        if (account.isGold()) {
            if (request.ratePerGram() == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "ratePerGram",
                        "Chit %s holds gold. Enter today's rate to say what it is worth."
                                .formatted(account.getChitNumber()));
            }
            redemption.setGrams(account.getBalanceGrams());
            redemption.setRatePerGram(Money.money(request.ratePerGram()));
        } else if (request.ratePerGram() != null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "ratePerGram",
                    "Chit %s holds money, not gold. It takes no rate."
                            .formatted(account.getChitNumber()));
        }
        redemption.setAmount(calculator.cashValue(
                account.getBalanceAmount(), redemption.getGrams(), redemption.getRatePerGram()));

        account.redeem(redemption);
        accountRepository.flush();
        log.info("Chit {} withdrawn as cash: {}{}", account.getChitNumber(), redemption.getAmount(),
                redemption.getGrams() == null ? "" : " for " + redemption.getGrams() + " g");
        return mapper.toAccount(requireDetailed(id));
    }

    /**
     * Abandons a chit before it matures.
     *
     * <p>The chit is kept, marked CANCELLED, as the record of what was paid in.
     * Refunding the customer is settled at the counter - the shop's chits are
     * not a deposit the application holds money against.
     */
    @Transactional
    public ChitDtos.Account cancel(Long id, String reason) {
        ChitAccount account = lock(id);
        if (account.getStatus() == ChitStatus.CANCELLED) {
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    "Chit %s is already cancelled.".formatted(account.getChitNumber()));
        }
        if (account.getStatus() == ChitStatus.CLOSED) {
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    "Chit %s has been spent and cannot be cancelled.".formatted(account.getChitNumber()));
        }
        account.setStatus(ChitStatus.CANCELLED);
        account.setClosedOn(businessClock.today());
        account.setCloseReason(StringNormalizer.trimToNull(reason));
        // Nothing is left to spend on a cancelled chit; what was paid in is
        // still on the record as paidAmount.
        account.setBalanceAmount(Money.ZERO);
        account.setBalanceGrams(Money.weight(BigDecimal.ZERO));
        accountRepository.flush();
        log.info("Chit {} cancelled by {} after {} instalment(s)", account.getChitNumber(),
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER), account.getPaidInstalments());
        return mapper.toAccount(requireDetailed(id));
    }

    /**
     * Erases a chit and gives its number back when it was the last issued.
     *
     * <p>The instalments go with it, by the cascade. Nothing else in the
     * application points at a chit until one is spent on an invoice, and a
     * spent chit is CLOSED, which this refuses.
     */
    @Transactional
    public void delete(Long id) {
        ChitAccount account = lock(id);
        if (account.getStatus() == ChitStatus.CLOSED) {
            // A chit closes either way it is redeemed, so the message has to
            // say which: one is undone by deleting an invoice, the other is
            // money already out of the drawer.
            boolean onInvoice = account.getRedemptions().stream()
                    .anyMatch(r -> r.isActive() && r.getKind() == RedemptionKind.SALE);
            throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                    onInvoice
                            ? ("Chit %s has been spent against an invoice and cannot be deleted. "
                                    + "Delete that invoice first.").formatted(account.getChitNumber())
                            : "Chit %s has been paid out and cannot be deleted."
                                    .formatted(account.getChitNumber()));
        }
        String number = account.getChitNumber();
        boolean numberFreed =
                documentNumberService.release(DocumentSeries.CHIT_ACCOUNT, account.getStartedOn(), number);

        accountRepository.delete(account);
        accountRepository.flush();
        log.warn("Chit {} DELETED by {}: {} instalment(s) erased, number {}", number,
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER),
                account.getPaidInstalments(),
                numberFreed ? "freed for reuse" : "not reusable - it was not the last issued");
    }

    // ------------------------------------------------------------- helpers ---

    /**
     * One instalment, money or gold.
     *
     * <p>Where the two kinds of chit actually differ: a gold instalment needs
     * the rate and records what it bought, and a money one must not be given a
     * rate at all. Refusing a rate on a money chit rather than ignoring it
     * stops a counter believing they have recorded something they have not.
     */
    private ChitPayment buildPayment(
            ChitAccount account, short instalmentNo, LocalDate paidOn,
            BigDecimal amount, BigDecimal ratePerGram, boolean paidByShop) {

        ChitPayment payment = new ChitPayment();
        payment.setInstalmentNo(instalmentNo);
        payment.setPaidOn(paidOn);
        payment.setAmount(Money.money(amount));
        payment.setPaidByShop(paidByShop);

        if (account.isGold()) {
            if (ratePerGram == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "ratePerGram",
                        "Chit %s saves in gold. Enter today's rate so the instalment can buy grams."
                                .formatted(account.getChitNumber()));
            }
            payment.setRatePerGram(Money.money(ratePerGram));
            payment.setGrams(calculator.gramsFor(payment.getAmount(), payment.getRatePerGram()));
        } else if (ratePerGram != null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "ratePerGram",
                    "Chit %s saves money, not gold. It takes no rate."
                            .formatted(account.getChitNumber()));
        }
        return payment;
    }

    /**
     * A chit can only be spent once it has matured and while something is left
     * on it. Saying which of the two is wrong matters at a counter: one means
     * "add the bonus first" and the other means "this chit is already gone".
     */
    private static void requireRedeemable(ChitAccount account) {
        if (account.isRedeemable()) {
            return;
        }
        String reason = switch (account.getStatus()) {
            case ACTIVE, COMPLETE -> "has not matured yet. Add the bonus to mature it first";
            case CLOSED -> "has already been spent in full";
            case CANCELLED -> "was cancelled";
            case MATURED -> "has nothing left on it";
        };
        throw new StateConflictException(ErrorCode.VALIDATION_FAILED, "status",
                "Chit %s %s.".formatted(account.getChitNumber(), reason));
    }

    private static PaymentMethod parseMethod(String method) {
        String trimmed = StringNormalizer.trimToNull(method);
        if (trimmed == null) {
            return null;
        }
        try {
            return PaymentMethod.valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "paymentMethod",
                    "%s is not a payment method.".formatted(method));
        }
    }

    private LocalDate resolveDate(LocalDate requested, String field) {
        LocalDate today = businessClock.today();
        if (requested == null) {
            return today;
        }
        if (requested.isAfter(today)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, field,
                    "The date cannot be in the future.");
        }
        return requested;
    }

    private ChitAccount requireDetailed(Long id) {
        return accountRepository.findDetailedById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Chit", id));
    }

    private ChitAccount lock(Long id) {
        return accountRepository.lockById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Chit", id));
    }

    private Specification<ChitAccount> matching(
            String search, ChitStatus status, ChitSchemeType schemeType, Long customerId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String term = StringNormalizer.trimToNull(search);
            if (term != null) {
                String pattern = "%" + term.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("chitNumber")), pattern),
                        builder.like(builder.lower(root.get("customer").get("fullName")), pattern),
                        builder.like(root.get("customer").get("mobileNumber"), "%" + term + "%")));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (schemeType != null) {
                predicates.add(builder.equal(root.get("schemeType"), schemeType));
            }
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customer").get("id"), customerId));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
