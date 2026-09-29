package com.jewellery.erp.report.service;

import static com.jewellery.erp.report.engine.ReportColumn.date;
import static com.jewellery.erp.report.engine.ReportColumn.dateTime;
import static com.jewellery.erp.report.engine.ReportColumn.integer;
import static com.jewellery.erp.report.engine.ReportColumn.money;
import static com.jewellery.erp.report.engine.ReportColumn.percent;
import static com.jewellery.erp.report.engine.ReportColumn.text;
import static com.jewellery.erp.report.engine.ReportColumn.weight;

import com.jewellery.erp.common.config.BusinessClock;
import com.jewellery.erp.report.engine.ReportColumn;
import com.jewellery.erp.report.engine.ReportDefinition;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The reports' SQL and columns. Filtering is always done in the database, on
 * indexed columns, with bound parameters - never by loading rows and filtering in
 * Java, and never by concatenating user input into SQL.
 */
@Component
public class ReportDefinitions {

    private static final List<ReportColumn> STOCK_COLUMNS = List.of(
            text("serialNumber", "Serial No", 10),
            dateTime("stockedAt", "Stocked On"),
            text("itemType", "Item Type", 12),
            text("purity", "Purity", 10),
            text("category", "Category", 16),
            text("subCategory", "Sub Category", 16),
            text("hsnCode", "HSN", 8),
            percent("gstPercentage", "GST %"),
            text("size", "Size", 10),
            weight("netWeightGrams", "Net Weight (g)", true),
            text("status", "Status", 10),
            text("active", "Active", 7),
            text("invoiceNumber", "Sold On Invoice", 18),
            date("invoiceDate", "Sold Date"));

    private static final String STOCK_SQL = """
            SELECT i.serial_number, i.created_at, it.name, p.name, c.name, sc.name, h.hsn_code, h.gst_percentage,
                   i.size, i.weight_grams, i.status, CASE WHEN i.active THEN 'Yes' ELSE 'No' END,
                   s.invoice_number, s.invoice_date
            FROM inventory_items i
            JOIN item_types it ON it.id = i.item_type_id
            JOIN purities p ON p.id = i.purity_id
            JOIN categories c ON c.id = i.category_id
            LEFT JOIN sub_categories sc ON sc.id = i.sub_category_id
            LEFT JOIN hsn_codes h ON h.id = i.hsn_id
            LEFT JOIN sale_items si ON si.inventory_item_id = i.id AND si.line_status = 'ACTIVE'
            LEFT JOIN sales s ON s.id = si.sale_id
            WHERE i.created_at >= :from AND i.created_at < :to
            """;

    private static final List<ReportColumn> INVOICE_COLUMNS = List.of(
            text("invoiceNumber", "Invoice No", 18),
            date("invoiceDate", "Date"),
            text("status", "Status", 11),
            text("customerCode", "Customer Code", 12),
            text("customerName", "Customer", 24),
            text("customerMobile", "Mobile", 13),
            integer("itemCount", "Items", true),
            weight("grossWeightGrams", "Gross Wt (g)", true),
            money("subtotal", "Taxable Amount", true),
            money("cgstAmount", "CGST", true),
            money("sgstAmount", "SGST", true),
            money("discountAmount", "Discount", true),
            money("grandTotal", "Grand Total", true),
            money("oldMetalAdjustmentAmount", "Old Gold/Silver", true),
            money("roundOffAmount", "Round Off", true),
            money("netPayable", "Net Payable", true),
            money("amountPaid", "Paid", true),
            money("balanceAmount", "Balance", true),
            text("paymentStatus", "Payment", 9),
            text("cancelReason", "Cancel Reason", 24));

    private static final String INVOICE_SQL = """
            SELECT s.invoice_number, s.invoice_date, s.status, cu.customer_code, s.customer_name, s.customer_mobile,
                   (SELECT count(*) FROM sale_items si WHERE si.sale_id = s.id),
                   (SELECT coalesce(sum(si.gross_weight_grams), 0) FROM sale_items si WHERE si.sale_id = s.id),
                   s.subtotal, s.cgst_amount, s.sgst_amount, s.discount_amount, s.grand_total,
                   s.old_metal_adjustment_amount, s.round_off_amount, s.net_payable, s.amount_paid,
                   s.balance_amount, s.payment_status, s.cancel_reason
            FROM sales s
            JOIN customers cu ON cu.id = s.customer_id
            """;

    private static final List<ReportColumn> ITEM_COLUMNS = List.of(
            text("invoiceNumber", "Invoice No", 18),
            date("invoiceDate", "Date"),
            integer("lineNumber", "Line", false),
            text("serialNumber", "Serial No", 10),
            text("particulars", "Particulars", 24),
            text("itemType", "Item Type", 12),
            text("purity", "Purity", 10),
            text("category", "Category", 16),
            text("subCategory", "Sub Category", 16),
            text("hsnCode", "HSN", 8),
            percent("gstPercentage", "GST %"),
            weight("netWeightGrams", "Net Wt (g)", true),
            percent("wastagePercentage", "Wastage %"),
            weight("wastageWeightGrams", "Wastage Wt (g)", true),
            weight("grossWeightGrams", "Gross Wt (g)", true),
            money("ratePerGram", "Rate / g", false),
            money("makingCharge", "Making Charge", true),
            money("amount", "Amount", true),
            money("cgstAmount", "CGST", true),
            money("sgstAmount", "SGST", true),
            money("discountAmount", "Discount", true),
            text("customerName", "Customer", 24),
            text("lineStatus", "Line Status", 11));

    private static final String ITEM_SQL = """
            SELECT s.invoice_number, s.invoice_date, si.line_number, si.serial_number, si.particulars,
                   it.name, p.name, c.name, sc.name, si.hsn_code, si.gst_percentage,
                   si.net_weight_grams, si.wastage_percentage, si.wastage_weight_grams, si.gross_weight_grams,
                   si.rate_per_gram, si.making_charge, si.amount, si.cgst_amount, si.sgst_amount,
                   si.discount_amount, s.customer_name, si.line_status
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            JOIN item_types it ON it.id = si.item_type_id
            JOIN purities p ON p.id = si.purity_id
            JOIN categories c ON c.id = si.category_id
            LEFT JOIN sub_categories sc ON sc.id = si.sub_category_id
            """;

    private static final List<ReportColumn> PAYMENT_COLUMNS = List.of(
            text("invoiceNumber", "Invoice No", 18),
            date("invoiceDate", "Invoice Date"),
            date("paymentDate", "Payment Date"),
            text("method", "Method", 14),
            money("amount", "Amount", true),
            text("referenceNumber", "Reference", 20),
            text("customerName", "Customer", 24),
            text("remarks", "Remarks", 24));

    private static final String PAYMENT_SQL = """
            SELECT s.invoice_number, s.invoice_date, sp.payment_date, sp.payment_method, sp.amount,
                   sp.reference_number, s.customer_name, sp.remarks
            FROM sale_payments sp
            JOIN sales s ON s.id = sp.sale_id
            """;

    private final BusinessClock businessClock;

    public ReportDefinitions(BusinessClock businessClock) {
        this.businessClock = businessClock;
    }

    public ReportDefinition stock(StockReportFilter filter, Map<String, String> labels) {
        Map<String, Object> params = new HashMap<>();
        // Stock-in timestamps are instants; the day boundaries are the shop's, not UTC's.
        params.put("from", Timestamp.from(businessClock.startOfDay(filter.startDate())));
        params.put("to", Timestamp.from(businessClock.startOfNextDay(filter.endDate())));

        StringBuilder sql = new StringBuilder(STOCK_SQL);
        List<String> descriptions = new ArrayList<>();
        if (filter.itemTypeId() != null) {
            sql.append(" AND i.item_type_id = :itemTypeId");
            params.put("itemTypeId", filter.itemTypeId());
            descriptions.add("Item type: " + labels.get("itemType"));
        }
        if (filter.categoryId() != null) {
            sql.append(" AND i.category_id = :categoryId");
            params.put("categoryId", filter.categoryId());
            descriptions.add("Category: " + labels.get("category"));
        }
        if (filter.status() != null) {
            sql.append(" AND i.status = :status");
            params.put("status", filter.status().name());
            descriptions.add("Status: " + filter.status().name());
        }
        if (!filter.includeInactive()) {
            sql.append(" AND i.active = TRUE");
        } else {
            descriptions.add("Including inactive items");
        }
        sql.append(" ORDER BY i.serial_number");

        return new ReportDefinition("Stock Report", "stock-report", filter.startDate(), filter.endDate(),
                descriptions, List.of(new ReportDefinition.Sheet("Stock", STOCK_COLUMNS, sql.toString(), params)));
    }

    public ReportDefinition sales(SalesReportFilter filter, Map<String, String> labels) {
        Map<String, Object> params = new HashMap<>();
        params.put("from", filter.startDate());
        params.put("to", filter.endDate());

        StringBuilder where = new StringBuilder(" WHERE s.invoice_date BETWEEN :from AND :to");
        List<String> descriptions = new ArrayList<>();
        if (filter.status() != null) {
            where.append(" AND s.status = :status");
            params.put("status", filter.status().name());
            descriptions.add("Status: " + filter.status().name());
        } else {
            descriptions.add("Including cancelled invoices");
        }
        if (filter.paymentStatus() != null) {
            where.append(" AND s.payment_status = :paymentStatus");
            params.put("paymentStatus", filter.paymentStatus().name());
            descriptions.add("Payment: " + filter.paymentStatus().name());
        }
        if (filter.customerId() != null) {
            where.append(" AND s.customer_id = :customerId");
            params.put("customerId", filter.customerId());
            descriptions.add("Customer: " + labels.get("customer"));
        }

        return new ReportDefinition("Sales Report", "sales-report", filter.startDate(), filter.endDate(),
                descriptions, List.of(
                        new ReportDefinition.Sheet("Invoices", INVOICE_COLUMNS,
                                INVOICE_SQL + where + " ORDER BY s.invoice_date, s.invoice_number", params),
                        new ReportDefinition.Sheet("Items", ITEM_COLUMNS,
                                ITEM_SQL + where + " ORDER BY s.invoice_date, s.invoice_number, si.line_number",
                                params),
                        new ReportDefinition.Sheet("Payments", PAYMENT_COLUMNS,
                                PAYMENT_SQL + where + " ORDER BY s.invoice_date, s.invoice_number, sp.id",
                                params)));
    }
}
