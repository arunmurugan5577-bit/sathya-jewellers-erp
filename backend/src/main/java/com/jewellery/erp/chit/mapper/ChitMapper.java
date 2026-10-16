package com.jewellery.erp.chit.mapper;

import com.jewellery.erp.chit.dto.ChitDtos;
import com.jewellery.erp.chit.entity.ChitAccount;
import com.jewellery.erp.chit.entity.ChitPayment;
import com.jewellery.erp.chit.entity.ChitScheme;
import com.jewellery.erp.chit.service.ChitCalculator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChitMapper {

    private final ChitCalculator calculator;

    public ChitMapper(ChitCalculator calculator) {
        this.calculator = calculator;
    }

    public ChitDtos.Scheme toScheme(ChitScheme scheme) {
        return new ChitDtos.Scheme(
                scheme.getId(),
                scheme.getCode(),
                scheme.getName(),
                scheme.getSchemeType().name(),
                scheme.getMonthlyAmount(),
                scheme.getPayableInstalments(),
                scheme.getTotalInstalments(),
                calculator.maturityAmount(scheme.getMonthlyAmount(), scheme.getTotalInstalments()),
                scheme.isActive());
    }

    public ChitDtos.Payment toPayment(ChitPayment payment) {
        return new ChitDtos.Payment(
                payment.getId(),
                payment.getInstalmentNo(),
                payment.getPaidOn(),
                payment.getAmount(),
                payment.getRatePerGram(),
                payment.getGrams(),
                payment.isPaidByShop(),
                payment.getPaymentMethod() == null ? null : payment.getPaymentMethod().name(),
                payment.getReferenceNo(),
                payment.getRemarks(),
                payment.getCreatedAt(),
                payment.getCreatedBy());
    }

    public ChitDtos.Account toAccount(ChitAccount account) {
        return new ChitDtos.Account(
                account.getId(),
                account.getChitNumber(),
                account.getCustomer().getId(),
                account.getCustomer().getCustomerCode(),
                account.getCustomer().getFullName(),
                account.getCustomer().getMobileNumber(),
                account.getScheme().getId(),
                account.getScheme().getCode(),
                account.getScheme().getName(),
                account.getSchemeType().name(),
                account.getMonthlyAmount(),
                account.getPayableInstalments(),
                account.getTotalInstalments(),
                account.getStartedOn(),
                account.getStatus().name(),
                account.getPaidInstalments(),
                account.instalmentsOwed(),
                account.getPaidAmount(),
                account.getAccruedGrams(),
                account.getBalanceAmount(),
                account.getBalanceGrams(),
                calculator.maturityAmount(account.getMonthlyAmount(), account.getTotalInstalments()),
                calculator.remainingToPay(
                        account.getMonthlyAmount(), account.getPayableInstalments(), account.getPaidInstalments()),
                account.getMaturedOn(),
                account.getClosedOn(),
                account.getCloseReason(),
                account.getRemarks(),
                account.getPayments().stream().map(this::toPayment).toList(),
                account.getCreatedAt(),
                account.getCreatedBy());
    }

    /** The list row. No payments: a page of twenty chits would fetch hundreds of instalments. */
    public ChitDtos.Summary toSummary(ChitAccount account) {
        return new ChitDtos.Summary(
                account.getId(),
                account.getChitNumber(),
                account.getCustomer().getFullName(),
                account.getCustomer().getMobileNumber(),
                account.getScheme().getName(),
                account.getSchemeType().name(),
                account.getMonthlyAmount(),
                account.getStartedOn(),
                account.getStatus().name(),
                account.getPaidInstalments(),
                account.getPayableInstalments(),
                account.getPaidAmount(),
                account.getAccruedGrams(),
                account.getBalanceAmount(),
                account.getBalanceGrams());
    }

    public List<ChitDtos.Scheme> toSchemes(List<ChitScheme> schemes) {
        return schemes.stream().map(this::toScheme).toList();
    }
}
