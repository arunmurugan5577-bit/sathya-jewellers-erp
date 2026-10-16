package com.jewellery.erp.chit.service;

import com.jewellery.erp.chit.entity.ChitAccount;
import com.jewellery.erp.chit.entity.ChitRedemption;
import com.jewellery.erp.chit.entity.RedemptionKind;
import com.jewellery.erp.chit.entity.RedemptionStatus;
import com.jewellery.erp.chit.repository.ChitAccountRepository;
import com.jewellery.erp.chit.repository.ChitRedemptionRepository;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.exception.StateConflictException;
import com.jewellery.erp.common.util.Money;
import com.jewellery.erp.sales.entity.Sale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spending a matured chit on an invoice.
 *
 * <p>Everything the sales side needs to know about chits, and nothing else, so
 * that {@code SaleService} deals with one small surface rather than the whole
 * chit module. It mirrors what {@code OldMetalService} already does for old
 * gold: look the credit up, check it belongs to this customer and has the
 * value claimed, take it off under a lock, and put it back if the invoice is
 * cancelled.
 *
 * <p>The two kinds of chit are asked for differently, and deliberately so. A
 * money chit is spent in rupees, which is all it holds. A gold chit is spent
 * in grams at the rate on the day: the customer bought gold at eleven or
 * twelve different rates and takes delivery of it at one, and asking for an
 * amount instead would mean dividing back into grams and losing a milligram
 * doing it.
 *
 * <p>GST is not this class's business. The chit comes off the grand total the
 * way old gold does, so the jewellery is taxed in full and the chit settles
 * what is payable - which is the same invoice the shop would raise if the
 * customer paid cash.
 */
@Service
@Transactional(readOnly = true)
public class ChitRedemptionService {

    private static final Logger log = LoggerFactory.getLogger(ChitRedemptionService.class);

    private final ChitAccountRepository accountRepository;
    private final ChitRedemptionRepository redemptionRepository;

    public ChitRedemptionService(
            ChitAccountRepository accountRepository, ChitRedemptionRepository redemptionRepository) {
        this.accountRepository = accountRepository;
        this.redemptionRepository = redemptionRepository;
    }

    /** What the counter asked to take off one chit. Figures are checked, never trusted. */
    public record SpendRequest(
            Long chitAccountId, BigDecimal amount, BigDecimal grams, BigDecimal ratePerGram) {}

    /** One priced redemption, ready to be written once the invoice has a number. */
    public record Spend(ChitAccount account, BigDecimal grams, BigDecimal ratePerGram, BigDecimal amount) {}

    // -------------------------------------------------------------- lookup ---

    /** For a preview: the chits as they stand, with no lock taken. */
    public Map<Long, ChitAccount> findRedeemable(Collection<Long> ids) {
        return check(accountRepository.findAllById(ids), ids);
    }

    /**
     * For a sale: the chits, locked until the invoice is committed.
     *
     * <p>Without this, two counters could each see 24,000 on the same chit and
     * each take it off an invoice. The second now waits here and then finds
     * the balance gone.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, ChitAccount> lockRedeemable(Collection<Long> ids) {
        return check(accountRepository.lockAllById(ids), ids);
    }

    private Map<Long, ChitAccount> check(List<ChitAccount> found, Collection<Long> ids) {
        Map<Long, ChitAccount> byId =
                found.stream().collect(Collectors.toMap(ChitAccount::getId, Function.identity()));
        for (Long id : ids) {
            ChitAccount account = byId.get(id);
            if (account == null) {
                throw ResourceNotFoundException.of("Chit", id);
            }
            if (!account.isRedeemable()) {
                throw new StateConflictException(ErrorCode.CHIT_NOT_REDEEMABLE, "chitRedemptions",
                        "Chit %s cannot be spent: it %s.".formatted(account.getChitNumber(), why(account)));
            }
        }
        return byId;
    }

    private static String why(ChitAccount account) {
        return switch (account.getStatus()) {
            case ACTIVE, COMPLETE -> "has not matured yet";
            case CLOSED -> "has already been spent in full";
            case CANCELLED -> "was cancelled";
            case MATURED -> "has nothing left on it";
        };
    }

    // -------------------------------------------------------------- pricing ---

    /**
     * Works out what each chit is worth on this invoice.
     *
     * <p>Every figure is derived here from the chit and the rate; the only
     * thing taken from the counter is how much of the chit to spend. A chit
     * belonging to somebody else is refused outright - a chit is not a gift
     * voucher.
     */
    public List<Spend> price(List<SpendRequest> requests, Map<Long, ChitAccount> chits, Long customerId) {
        List<Spend> spends = new ArrayList<>(requests.size());
        List<Long> seen = new ArrayList<>(requests.size());
        for (SpendRequest request : requests) {
            ChitAccount account = chits.get(request.chitAccountId());
            if (account == null) {
                throw ResourceNotFoundException.of("Chit", request.chitAccountId());
            }
            if (seen.contains(account.getId())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "chitRedemptions",
                        "Chit %s is on this invoice more than once.".formatted(account.getChitNumber()));
            }
            seen.add(account.getId());

            if (customerId != null && !account.getCustomer().getId().equals(customerId)) {
                throw new BusinessRuleException(ErrorCode.CHIT_CUSTOMER_MISMATCH, "chitRedemptions",
                        "Chit %s belongs to %s and cannot be spent by another customer."
                                .formatted(account.getChitNumber(), account.getCustomer().getFullName()));
            }
            spends.add(account.isGold() ? gold(account, request) : money(account, request));
        }
        return spends;
    }

    private Spend gold(ChitAccount account, SpendRequest request) {
        if (request.grams() == null || request.ratePerGram() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "chitRedemptions",
                    ("Chit %s holds gold. Say how many grams to take and today's rate; "
                            + "the value follows from those.").formatted(account.getChitNumber()));
        }
        if (request.amount() != null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "chitRedemptions",
                    "Chit %s holds gold, so its value is worked out from the grams and the rate."
                            .formatted(account.getChitNumber()));
        }
        BigDecimal grams = Money.weight(request.grams());
        if (grams.compareTo(account.getBalanceGrams()) > 0) {
            throw new StateConflictException(ErrorCode.CHIT_BALANCE_EXCEEDED, "chitRedemptions",
                    "Chit %s holds %s g; %s g was asked of it."
                            .formatted(account.getChitNumber(), account.getBalanceGrams(), grams));
        }
        BigDecimal rate = Money.money(request.ratePerGram());
        return new Spend(account, grams, rate, Money.rupees(grams.multiply(rate)));
    }

    private Spend money(ChitAccount account, SpendRequest request) {
        if (request.grams() != null || request.ratePerGram() != null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "chitRedemptions",
                    "Chit %s holds money, not gold. It takes neither grams nor a rate."
                            .formatted(account.getChitNumber()));
        }
        if (request.amount() == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "chitRedemptions",
                    "Say how much of chit %s to put against this invoice."
                            .formatted(account.getChitNumber()));
        }
        BigDecimal amount = Money.money(request.amount());
        if (amount.compareTo(account.getBalanceAmount()) > 0) {
            throw new StateConflictException(ErrorCode.CHIT_BALANCE_EXCEEDED, "chitRedemptions",
                    "Chit %s has %s left; %s was asked of it."
                            .formatted(account.getChitNumber(), account.getBalanceAmount(), amount));
        }
        return new Spend(account, null, null, amount);
    }

    /** What the priced redemptions come to, for the invoice totals. */
    public BigDecimal total(List<Spend> spends) {
        return spends.stream().map(Spend::amount).reduce(Money.ZERO, BigDecimal::add);
    }

    // --------------------------------------------------------------- writing ---

    /**
     * Takes the value off the chits, now that the invoice exists to point at.
     *
     * <p>Called after the invoice is saved rather than before, because a
     * redemption names the invoice it paid for and there is no invoice to name
     * until then. The chits are already locked by {@link #lockRedeemable}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(List<Spend> spends, Sale sale, LocalDate on) {
        for (Spend spend : spends) {
            ChitRedemption redemption = new ChitRedemption();
            redemption.setRedeemedOn(on);
            redemption.setKind(RedemptionKind.SALE);
            redemption.setSale(sale);
            redemption.setGrams(spend.grams());
            redemption.setRatePerGram(spend.ratePerGram());
            redemption.setAmount(spend.amount());

            spend.account().redeem(redemption);
            // Both sides, or the invoice this method was handed comes back
            // without its chit on it: a Sale created in this transaction has
            // an empty collection in memory and Hibernate never reads the rows
            // it does not own.
            sale.getChitRedemptions().add(redemption);
            log.info("Chit {} spent on invoice {}: {}{}", spend.account().getChitNumber(),
                    sale.getInvoiceNumber(), spend.amount(),
                    spend.grams() == null ? "" : " for " + spend.grams() + " g");
        }
        if (!spends.isEmpty()) {
            accountRepository.flush();
        }
    }

    /**
     * Puts back what an invoice took off chits.
     *
     * <p>Used when an invoice is cancelled and when one is deleted: either way
     * the customer never got the jewellery, so they still have the chit. The
     * redemption row is kept and marked cancelled rather than erased, so the
     * chit's history shows that it was spent and handed back - except on a
     * delete, where the invoice is going and the row goes with it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal releaseForSale(Long saleId) {
        List<ChitRedemption> redemptions =
                redemptionRepository.findBySaleIdAndStatusOrderByIdAsc(saleId, RedemptionStatus.ACTIVE);
        BigDecimal released = Money.ZERO;
        for (ChitRedemption redemption : redemptions) {
            ChitAccount account = accountRepository.lockById(redemption.getChitAccount().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Chit", redemption.getChitAccount().getId()));
            account.returnRedemption(redemption);
            released = released.add(redemption.getAmount());
            log.info("Chit {}: {} put back from invoice {}", account.getChitNumber(),
                    redemption.getAmount(), saleId);
        }
        if (!redemptions.isEmpty()) {
            accountRepository.flush();
        }
        return released;
    }

    /** The chits an invoice was paid with, for showing on it. */
    public List<ChitRedemption> activeForSale(Long saleId) {
        return redemptionRepository.findBySaleIdAndStatusOrderByIdAsc(saleId, RedemptionStatus.ACTIVE);
    }
}
