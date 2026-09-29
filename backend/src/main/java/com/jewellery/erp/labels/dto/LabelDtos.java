package com.jewellery.erp.labels.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Request and response shapes for barcode label printing. */
public final class LabelDtos {

    private LabelDtos() {}

    /**
     * The pieces to print, by inventory id.
     *
     * <p>Ids, never serial numbers or barcode values: everything printed is read
     * from the inventory row on the server, so what the label carries cannot be
     * chosen by the caller.
     */
    @Schema(name = "LabelRequest")
    public record Request(
            @NotEmpty(message = "Select at least one item to print")
            @Size(max = 200, message = "At most 200 labels can be printed at once")
            List<@NotNull Long> inventoryItemIds,

            // Bounded on purpose. The item list is capped at 200, but without a
            // cap here a mistyped copy count spools thousands of tags through a
            // thermal printer before anyone can stop it.
            // Only the upper end is enforced. Anything below one already means
            // "one" (see copiesOrOne), and that leniency is older than this cap.
            @Schema(example = "1", description = "How many copies of each label, up to 20. Defaults to 1.")
            @Max(value = 20, message = "At most 20 copies of a label can be printed at once")
            Integer copies,

            @Schema(example = "1", description = "1-based tag to start at, to use up a part-printed row.")
            @Min(value = 1, message = "Start tag must be 1 or more")
            @Max(value = 10, message = "Start tag must be 10 or less")
            Integer startColumn) {

        public int startColumnOrFirst() {
            return startColumn == null || startColumn < 1 ? 1 : startColumn;
        }

        public int copiesOrOne() {
            return copies == null || copies < 1 ? 1 : copies;
        }

        /** Labels this request would produce, for the run-length check. */
        public int totalLabels() {
            return inventoryItemIds == null ? 0 : inventoryItemIds.size() * copiesOrOne();
        }
    }

    /** One label, ready to show on screen. */
    @Schema(name = "Label")
    public record Label(
            Long inventoryItemId,
            @Schema(example = "000123") String serialNumber,
            @Schema(example = "22K / 916", description = "Printed after the serial number; gold only")
            String purityText,
            @Schema(example = "KOLUSU", description = "Sub category, or category, as printed") String particulars,
            @Schema(example = "32.130") BigDecimal weightGrams,
            @Schema(example = "11") String size,
            @Schema(example = "Gold") String itemTypeName,
            @Schema(example = "Ring") String categoryName,
            String subCategoryName,
            @Schema(example = "SJ") String shopShortName,
            @Schema(example = "AVAILABLE") String status) {}

    /** A piece that cannot be printed, and why. */
    @Schema(name = "LabelProblem")
    public record Problem(Long inventoryItemId, String serialNumber, String message) {}

    /** What a direct print did. */
    @Schema(name = "LabelPrintResult")
    public record PrintResult(
            @Schema(example = "5") int printed,
            @Schema(example = "SNBC TVSE LP 46 NEO BPLE") String printer,
            @Schema(example = "true", description = "True when the pages were queued for the shop agent")
            boolean queued) {}

    /** Printers this computer knows about, for the settings screen. */
    @Schema(name = "LabelPrinters")
    public record Printers(List<String> printers, String defaultPrinter, String configured) {}

    // --- the shop print agent ----------------------------------------------

    /** The agent checking in and saying what printers the shop PC has. */
    @Schema(name = "LabelAgentHello")
    public record AgentHello(
            @Size(max = 40) String agentVersion,
            @Size(max = 160) String hostName,
            @Size(max = 50, message = "At most 50 printers can be reported") List<String> printers,
            @Size(max = 160) String defaultPrinter) {}

    /** What the server tells the agent in reply. */
    @Schema(name = "LabelAgentPoll")
    public record AgentPoll(@Schema(example = "3", description = "Pages waiting to be printed") long pending) {}

    /** One page for the agent to spool, as a base64 PNG. */
    @Schema(name = "LabelQueuedPage")
    public record QueuedPage(
            Long id,
            int pageNo,
            @Schema(description = "1-bit PNG at the printer resolution, base64") String imageBase64,
            BigDecimal widthMm,
            BigDecimal heightMm,
            @Schema(description = "Empty means the agent's default printer") String printerName) {}

    /** What the agent says happened to a page. */
    @Schema(name = "LabelPageResult")
    public record PageResult(
            boolean printed,
            @Size(max = 500) String error) {}

    /** Whether the shop PC is listening, and what it can print to. */
    @Schema(name = "LabelAgentStatus")
    public record AgentStatus(
            @Schema(example = "true", description = "Heard from within the last 90 seconds") boolean online,
            java.time.Instant lastSeenAt,
            String hostName,
            String agentVersion,
            List<String> printers,
            String defaultPrinter,
            long pendingPages,
            long failedPages) {}

    @Schema(name = "LabelPreview")
    public record Preview(List<Label> labels, List<Problem> problems, Settings settings) {}

    @Schema(name = "LabelSettings")
    public record Settings(
            @Schema(example = "SJ") String shopShortName,
            BigDecimal labelWidthMm,
            BigDecimal labelHeightMm,
            @Schema(example = "5", description = "Tags side by side across the roll") short labelsAcross,
            @Schema(description = "Printable length of the tag, from its left edge") BigDecimal contentWidthMm,
            @Schema(description = "Printable height at the top of each tag") BigDecimal contentHeightMm,
            BigDecimal marginTopMm,
            BigDecimal marginLeftMm,
            BigDecimal offsetXMm,
            BigDecimal offsetYMm,
            @Schema(example = "0", description = "0, 90, 180 or 270") short rotationDegrees,
            BigDecimal barcodeHeightMm,
            @Schema(description = "Width of the narrowest bar in mm") BigDecimal barcodeModuleMm,
            BigDecimal serialFontPt,
            BigDecimal purityFontPt,
            BigDecimal shopFontPt,
            @Schema(example = "LOGO", description = "LOGO, TEXT (the short name) or NONE") String shopMark,
            @Schema(description = "Square box the logo is drawn in") BigDecimal shopLogoHeightMm,
            @Schema(description = "Type size of the name, weight and size block") BigDecimal detailFontPt,
            @Schema(example = "SNBC TVSE LP 46 NEO BPLE", description = "Windows printer; empty uses the default")
            String printerName,
            @Schema(example = "AGENT", description = "DIRECT prints from the server; AGENT queues for the shop PC")
            String printMode,
            boolean showPurity,
            @Schema(example = "CODE128", description = "Fixed: the barcode symbology used") String barcodeType) {}

    @Schema(name = "LabelSettingsRequest")
    public record SettingsRequest(
            @NotBlank(message = "Shop short name is required")
            @Size(max = 8, message = "Short name must not exceed 8 characters")
            String shopShortName,

            @NotNull @DecimalMin(value = "10", message = "Label width must be at least 10 mm")
            @DecimalMax(value = "200", message = "Label width must not exceed 200 mm")
            @Digits(integer = 4, fraction = 2) BigDecimal labelWidthMm,

            @NotNull @DecimalMin(value = "8", message = "Label height must be at least 8 mm")
            @DecimalMax(value = "200", message = "Label height must not exceed 200 mm")
            @Digits(integer = 4, fraction = 2) BigDecimal labelHeightMm,

            @NotNull(message = "Tags across is required")
            @Min(value = 1, message = "There must be at least one tag across")
            @Max(value = 10, message = "At most 10 tags across")
            Short labelsAcross,

            @NotNull @DecimalMin(value = "5", message = "Printable length must be at least 5 mm")
            @DecimalMax(value = "400", message = "Printable length must not exceed 400 mm")
            @Digits(integer = 4, fraction = 2) BigDecimal contentWidthMm,

            @NotNull @DecimalMin(value = "2", message = "Printable height must be at least 2 mm")
            @DecimalMax(value = "200", message = "Printable height must not exceed 200 mm")
            @Digits(integer = 4, fraction = 2) BigDecimal contentHeightMm,

            @NotNull @DecimalMin(value = "0", message = "Top margin cannot be negative")
            @Digits(integer = 4, fraction = 2) BigDecimal marginTopMm,

            // A negative left margin is allowed on purpose: it pulls the artwork
            // back towards the start of the tag, which is the only way to undo a
            // leading margin the printer driver adds of its own accord.
            @NotNull @DecimalMin(value = "-200", message = "Left margin must be between -200 and 200 mm")
            @DecimalMax(value = "200", message = "Left margin must be between -200 and 200 mm")
            @Digits(integer = 4, fraction = 2) BigDecimal marginLeftMm,

            @NotNull @DecimalMin(value = "-20", message = "Horizontal offset must be between -20 and 20 mm")
            @DecimalMax(value = "20", message = "Horizontal offset must be between -20 and 20 mm")
            @Digits(integer = 2, fraction = 2) BigDecimal offsetXMm,

            @NotNull @DecimalMin(value = "-20", message = "Vertical offset must be between -20 and 20 mm")
            @DecimalMax(value = "20", message = "Vertical offset must be between -20 and 20 mm")
            @Digits(integer = 2, fraction = 2) BigDecimal offsetYMm,

            @NotNull(message = "Rotation is required") Short rotationDegrees,

            @NotNull @DecimalMin(value = "3", message = "Barcode height must be at least 3 mm")
            @DecimalMax(value = "100", message = "Barcode height must not exceed 100 mm")
            @Digits(integer = 3, fraction = 2) BigDecimal barcodeHeightMm,

            @NotNull @DecimalMin(value = "0.10", message = "Narrow bar must be at least 0.10 mm")
            @DecimalMax(value = "1.00", message = "Narrow bar must not exceed 1.00 mm")
            @Digits(integer = 1, fraction = 3) BigDecimal barcodeModuleMm,

            @NotNull @DecimalMin(value = "3") @DecimalMax(value = "30")
            @Digits(integer = 2, fraction = 2) BigDecimal serialFontPt,

            @NotNull @DecimalMin(value = "3") @DecimalMax(value = "30")
            @Digits(integer = 2, fraction = 2) BigDecimal purityFontPt,

            @NotNull @DecimalMin(value = "3") @DecimalMax(value = "30")
            @Digits(integer = 2, fraction = 2) BigDecimal shopFontPt,

            @NotNull(message = "Choose what the tag carries for the shop")
            @Pattern(regexp = "TEXT|LOGO|NONE", message = "Shop mark must be LOGO, TEXT or NONE")
            String shopMark,

            @NotNull @DecimalMin(value = "2", message = "The logo box must be at least 2 mm")
            @DecimalMax(value = "40", message = "The logo box must not exceed 40 mm")
            @Digits(integer = 3, fraction = 2) BigDecimal shopLogoHeightMm,

            @NotNull @DecimalMin(value = "3") @DecimalMax(value = "30")
            @Digits(integer = 2, fraction = 2) BigDecimal detailFontPt,

            @Size(max = 160, message = "Printer name must not exceed 160 characters") String printerName,

            @NotNull(message = "Choose where the labels are printed")
            @Pattern(regexp = "DIRECT|AGENT", message = "Print mode must be DIRECT or AGENT")
            String printMode,

            boolean showPurity) {}
}
