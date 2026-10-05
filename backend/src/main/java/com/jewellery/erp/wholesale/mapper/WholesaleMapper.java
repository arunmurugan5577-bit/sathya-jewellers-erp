package com.jewellery.erp.wholesale.mapper;

import com.jewellery.erp.common.util.AmountInWords;
import com.jewellery.erp.shop.dto.ShopSettingsDto;
import com.jewellery.erp.shop.service.SellerSnapshot;
import com.jewellery.erp.wholesale.dto.WholesaleDtos;
import com.jewellery.erp.wholesale.entity.WholesaleEstimate;
import com.jewellery.erp.wholesale.entity.WholesaleEstimateItem;
import java.util.List;
import org.springframework.stereotype.Component;

/** Turns a stored estimate into what the screen and the printed slip show. */
@Component
public class WholesaleMapper {

    public WholesaleDtos.Detail toDetail(WholesaleEstimate estimate, ShopSettingsDto shop) {
        SellerSnapshot seller = SellerSnapshot.of(shop);
        return new WholesaleDtos.Detail(
                estimate.getId(),
                estimate.getEstimateNumber(),
                estimate.getEstimateDate(),
                estimate.getStatus().name(),
                estimate.getCustomer().getId(),
                estimate.getCustomerCode(),
                estimate.getCustomerName(),
                estimate.getCustomerMobile(),
                seller.name(),
                seller.address(),
                seller.mobile(),
                estimate.getPureRatePerGram(),
                estimate.getItems().stream().map(WholesaleMapper::toLine).toList(),
                toTotals(estimate),
                estimate.getRemarks(),
                estimate.getCancelReason(),
                estimate.getCancelledAt(),
                estimate.getCancelledBy(),
                estimate.getCreatedAt(),
                estimate.getCreatedBy());
    }

    public WholesaleDtos.Summary toSummary(WholesaleEstimate estimate) {
        return new WholesaleDtos.Summary(
                estimate.getId(),
                estimate.getEstimateNumber(),
                estimate.getEstimateDate(),
                estimate.getCustomerName(),
                estimate.getTotalPureGrams(),
                estimate.getTotalAmount(),
                estimate.getClosingPureGrams(),
                estimate.getClosingValue(),
                estimate.getStatus().name());
    }

    public static List<WholesaleDtos.Line> toLines(WholesaleEstimate estimate) {
        return estimate.getItems().stream().map(WholesaleMapper::toLine).toList();
    }

    private static WholesaleDtos.Line toLine(WholesaleEstimateItem item) {
        return new WholesaleDtos.Line(
                item.getLineNumber(),
                item.getInventoryItem().getId(),
                item.getSerialNumber(),
                item.getJewelName(),
                item.getJewelWeightGrams(),
                item.getPurePercentage(),
                item.getPureWeightGrams(),
                item.getRatePerGram(),
                item.getMakingCharge(),
                item.getStoneAmount(),
                item.getItemAmount(),
                item.getLineStatus().name());
    }

    private static WholesaleDtos.Totals toTotals(WholesaleEstimate e) {
        return new WholesaleDtos.Totals(
                e.getOpeningPureGrams(),
                e.getOpeningMiscAmount(),
                e.getOpeningValue(),
                e.getTotalPureGrams(),
                e.getTotalMiscAmount(),
                e.getTotalAmount(),
                e.getClosingPureGrams(),
                e.getClosingMiscAmount(),
                e.getClosingValue(),
                AmountInWords.rupees(e.getTotalAmount()));
    }
}
