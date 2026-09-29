package com.jewellery.erp.oldmetal.mapper;

import com.jewellery.erp.common.util.AmountInWords;
import com.jewellery.erp.oldmetal.dto.OldMetalDtos;
import com.jewellery.erp.oldmetal.entity.OldMetalTransaction;
import com.jewellery.erp.oldmetal.entity.OldMetalTransactionItem;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OldMetalMapper {

    public OldMetalDtos.Detail toDetail(OldMetalTransaction t) {
        return new OldMetalDtos.Detail(
                t.getId(),
                t.getTransactionNumber(),
                t.getTransactionDate(),
                t.getCustomer().getId(),
                t.getCustomerName(),
                t.getCustomerMobile(),
                t.getCustomerAddress(),
                t.getSellerName(),
                t.getSellerAddress(),
                t.getSellerMobile(),
                t.getSellerGstin(),
                t.getTotalAmount(),
                t.getUsedAmount(),
                t.availableAmount(),
                t.getStatus().name(),
                AmountInWords.rupees(t.getTotalAmount()),
                t.getRemarks(),
                t.getCancelReason(),
                t.getCancelledAt(),
                t.getCancelledBy(),
                t.getItems().stream().map(this::toItem).toList(),
                t.getCreatedAt(),
                t.getCreatedBy());
    }

    public OldMetalDtos.Summary toSummary(OldMetalTransaction t) {
        return new OldMetalDtos.Summary(
                t.getId(),
                t.getTransactionNumber(),
                t.getTransactionDate(),
                t.getCustomer().getId(),
                t.getCustomerName(),
                t.getCustomerMobile(),
                t.getTotalAmount(),
                t.getUsedAmount(),
                t.availableAmount(),
                t.getStatus().name());
    }

    public OldMetalDtos.Option toOption(OldMetalTransaction t) {
        String description = t.getItems().stream()
                .map(item -> "%s %s g @ %s".formatted(
                        item.getParticulars(), item.getNetWeightGrams().toPlainString(),
                        item.getRatePerGram().toPlainString()))
                .collect(Collectors.joining(", "));
        return new OldMetalDtos.Option(
                t.getId(),
                t.getTransactionNumber(),
                t.getTransactionDate(),
                description,
                t.getTotalAmount(),
                t.getUsedAmount(),
                t.availableAmount(),
                t.getStatus().name());
    }

    private OldMetalDtos.Item toItem(OldMetalTransactionItem item) {
        return new OldMetalDtos.Item(
                item.getLineNumber(),
                item.getItemType().getId(),
                item.getItemType().getName(),
                item.getPurity() == null ? null : item.getPurity().getId(),
                item.getPurity() == null ? null : item.getPurity().getName(),
                item.getParticulars(),
                item.getHsnCode(),
                item.getNetWeightGrams(),
                item.getGrossWeightGrams(),
                item.getRatePerGram(),
                item.getAmount());
    }
}
