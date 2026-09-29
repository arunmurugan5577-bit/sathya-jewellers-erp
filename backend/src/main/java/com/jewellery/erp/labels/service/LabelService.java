package com.jewellery.erp.labels.service;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.inventory.repository.InventoryItemRepository;
import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.entity.LabelPrintJob;
import com.jewellery.erp.labels.entity.LabelSettings;
import com.jewellery.erp.labels.entity.PrintMode;
import com.jewellery.erp.labels.entity.ShopMark;
import com.jewellery.erp.labels.repository.LabelPrintJobRepository;
import com.jewellery.erp.labels.repository.LabelSettingsRepository;
import com.jewellery.erp.security.SecurityUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Barcode labels for inventory pieces.
 *
 * <p>The one rule this service exists to keep: <b>a label's barcode is the
 * piece's serial number, read from the inventory row, and nothing else</b>. The
 * client sends inventory ids; the serial number, purity and shop short name are
 * all looked up here, so no caller can print a tag that says something the
 * stock record does not.
 */
@Service
@Transactional(readOnly = true)
public class LabelService {

    private static final Logger log = LoggerFactory.getLogger(LabelService.class);
    public static final String BARCODE_TYPE = "CODE128";

    private final InventoryItemRepository inventoryItemRepository;
    private final LabelSettingsRepository settingsRepository;
    private final LabelPrintJobRepository printJobRepository;
    private final PurityLabelFormatter purityFormatter;
    private final LabelDocumentBuilder documentBuilder;
    private final Code128Renderer barcodeRenderer;
    private final LabelPrinterService printerService;
    private final ShopLogo shopLogo;
    private final LabelQueueService queueService;

    public LabelService(
            InventoryItemRepository inventoryItemRepository,
            LabelSettingsRepository settingsRepository,
            LabelPrintJobRepository printJobRepository,
            PurityLabelFormatter purityFormatter,
            LabelDocumentBuilder documentBuilder,
            Code128Renderer barcodeRenderer,
            LabelPrinterService printerService,
            ShopLogo shopLogo,
            LabelQueueService queueService) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.settingsRepository = settingsRepository;
        this.printJobRepository = printJobRepository;
        this.purityFormatter = purityFormatter;
        this.documentBuilder = documentBuilder;
        this.barcodeRenderer = barcodeRenderer;
        this.printerService = printerService;
        this.shopLogo = shopLogo;
        this.queueService = queueService;
    }

    // ------------------------------------------------------------- settings ---

    public LabelDtos.Settings findSettings() {
        return toDto(requireSettings());
    }

    @Transactional
    public LabelDtos.Settings updateSettings(LabelDtos.SettingsRequest request) {
        LabelSettings settings = requireSettings();
        if (request.marginTopMm().compareTo(request.contentHeightMm()) >= 0
                || request.marginLeftMm().multiply(BigDecimal.valueOf(2)).compareTo(request.labelWidthMm()) >= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "marginTopMm",
                    "The margins must be smaller than the printable area of the tag.");
        }
        if (request.marginLeftMm().negate().compareTo(request.labelWidthMm()) >= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "marginLeftMm",
                    "A negative left margin cannot pull the print further back than the width of the tag.");
        }
        if (request.contentWidthMm().compareTo(request.labelWidthMm()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "contentWidthMm",
                    "The printable length cannot be more than the tag width.");
        }
        if (request.contentHeightMm().compareTo(request.labelHeightMm()) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "contentHeightMm",
                    "The printable height cannot be more than the tag length.");
        }
        settings.setShopShortName(StringNormalizer.trimToNull(request.shopShortName()));
        settings.setLabelWidthMm(request.labelWidthMm());
        settings.setLabelHeightMm(request.labelHeightMm());
        settings.setLabelsAcross(request.labelsAcross());
        settings.setContentWidthMm(request.contentWidthMm());
        settings.setContentHeightMm(request.contentHeightMm());
        settings.setMarginTopMm(request.marginTopMm());
        settings.setMarginLeftMm(request.marginLeftMm());
        settings.setOffsetXMm(request.offsetXMm());
        settings.setOffsetYMm(request.offsetYMm());
        settings.setRotationDegrees(request.rotationDegrees());
        settings.setBarcodeHeightMm(request.barcodeHeightMm());
        settings.setBarcodeModuleMm(request.barcodeModuleMm());
        settings.setSerialFontPt(request.serialFontPt());
        settings.setPurityFontPt(request.purityFontPt());
        settings.setShopFontPt(request.shopFontPt());
        settings.setShopMark(ShopMark.valueOf(request.shopMark()));
        settings.setShopLogoHeightMm(request.shopLogoHeightMm());
        settings.setDetailFontPt(request.detailFontPt());
        settings.setPrinterName(StringNormalizer.trimToNull(request.printerName()));
        settings.setPrintMode(PrintMode.valueOf(request.printMode()));
        settings.setShowPurity(request.showPurity());
        log.info("Label settings updated: {} x {} mm, short name '{}'",
                settings.getLabelWidthMm(), settings.getLabelHeightMm(), settings.getShopShortName());
        return toDto(settings);
    }

    // --------------------------------------------------------------- labels ---

    /** What would be printed, and what cannot be - shown before anything reaches the printer. */
    public LabelDtos.Preview preview(LabelDtos.Request request) {
        LabelSettings settings = requireSettings();
        List<LabelDtos.Problem> problems = new ArrayList<>();
        List<LabelDtos.Label> labels = resolve(request, settings, problems);
        return new LabelDtos.Preview(labels, problems, toDto(settings));
    }

    /** The printable document for a selection, without recording a print. */
    public String previewDocument(LabelDtos.Request request) {
        LabelSettings settings = requireSettings();
        return documentBuilder.build(
                requireLabels(request, settings), settings, request.startColumnOrFirst(), true);
    }

    /**
     * The document that goes to the printer, and the audit row that says it did.
     *
     * <p>The row is written when the document is handed over, which is the last
     * moment the server knows anything: whether the driver then put ink on a tag
     * is between the browser, Windows and the printer.
     */
    @Transactional
    public String printDocument(LabelDtos.Request request) {
        LabelSettings settings = requireSettings();
        List<LabelDtos.Label> labels = requireLabels(request, settings);

        recordPrint(labels);
        return documentBuilder.build(labels, settings, request.startColumnOrFirst(), false);
    }

    /**
     * Printers the labels can actually go to.
     *
     * <p>Hosted, that is whatever the shop's agent last reported - the server's
     * own list would be a data centre's, which is to say empty.
     */
    public LabelDtos.Printers printers() {
        LabelSettings settings = requireSettings();
        if (settings.getPrintMode() == PrintMode.AGENT) {
            LabelDtos.AgentStatus agent = queueService.status();
            return new LabelDtos.Printers(agent.printers(), agent.defaultPrinter(), settings.getPrinterName());
        }
        return new LabelDtos.Printers(
                printerService.availablePrinters(), printerService.defaultPrinter(), settings.getPrinterName());
    }

    /** Whether the shop PC is listening, for the printing screen to show. */
    public LabelDtos.AgentStatus agentStatus() {
        return queueService.status();
    }

    /**
     * Sends the labels straight to the label printer - no browser, no dialog.
     *
     * <p>The run is recorded before the job is handed over, for the same reason
     * the browser path records it: that is the last moment the application knows
     * anything. Whether the driver then put ink on a tag is between Windows and
     * the printer.
     */
    @Transactional
    public LabelDtos.PrintResult printDirect(LabelDtos.Request request) {
        LabelSettings settings = requireSettings();
        List<LabelDtos.Label> labels = requireLabels(request, settings);
        Long jobId = recordPrint(labels);

        if (settings.getPrintMode() == PrintMode.AGENT) {
            // Refused rather than queued: pages piling up for an agent that is
            // not running looks like a printer fault to whoever is at the
            // counter, and they would keep pressing Print.
            LabelDtos.AgentStatus agent = queueService.status();
            if (!agent.online()) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "printerName",
                        "The print agent on the shop computer is not running, so nothing can be printed. "
                                + "Start it on the counter PC and try again.");
            }
            int pages = queueService.enqueue(jobId, labels, settings);
            String printer = settings.getPrinterName() == null || settings.getPrinterName().isBlank()
                    ? agent.defaultPrinter()
                    : settings.getPrinterName();
            log.info("{} label(s) on {} page(s) queued for the shop agent", labels.size(), pages);
            return new LabelDtos.PrintResult(labels.size(), printer, true);
        }

        String printer = printerService.print(labels, settings);
        return new LabelDtos.PrintResult(labels.size(), printer, false);
    }

    // -------------------------------------------------------------- helpers ---

    /** Labels for printing: every selected piece must be printable, or nothing is. */
    private List<LabelDtos.Label> requireLabels(LabelDtos.Request request, LabelSettings settings) {
        List<LabelDtos.Problem> problems = new ArrayList<>();
        List<LabelDtos.Label> labels = resolve(request, settings, problems);
        if (!problems.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "inventoryItemIds",
                    problems.size() == 1
                            ? problems.get(0).message()
                            : "%d of the selected items cannot be printed.".formatted(problems.size()));
        }
        if (labels.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "inventoryItemIds",
                    "Select at least one item to print.");
        }
        return labels;
    }

    /**
     * Turns the selection into labels, collecting anything unprintable rather than
     * failing at the first bad row - the counter wants to see every problem at once.
     */
    private List<LabelDtos.Label> resolve(
            LabelDtos.Request request, LabelSettings settings, List<LabelDtos.Problem> problems) {

        // A piece selected twice is one piece; copies are asked for explicitly.
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(request.inventoryItemIds()));
        Map<Long, InventoryItem> found = inventoryItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));

        String shortName = settings.getShopShortName();
        if (shortName == null || shortName.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "shopShortName",
                    "Set the shop short name in label settings before printing.");
        }

        List<LabelDtos.Label> labels = new ArrayList<>();
        for (Long id : ids) {
            InventoryItem item = found.get(id);
            if (item == null) {
                problems.add(new LabelDtos.Problem(id, null, "Item %d is no longer in the inventory.".formatted(id)));
                continue;
            }
            String serial = item.getSerialNumber() == null ? "" : item.getSerialNumber().trim();
            if (serial.isEmpty()) {
                problems.add(new LabelDtos.Problem(id, null, "This piece has no serial number to put in a barcode."));
                continue;
            }
            // Gold shows its purity after the serial number; other metals do not
            // carry one on the tag, so a missing one is not a reason to refuse.
            String purityText = settings.isShowPurity() ? purityFormatter.labelText(item.getPurity()) : null;
            // A long serial number makes a wide barcode. Better to say so now than
            // to print a tag whose bars run off the edge, or get squeezed until no
            // scanner will read them.
            // The short name moves above the barcode when it will not fit beside it,
            // so the barcode only has to fit the tag's own printable width.
            BigDecimal barcodeWidth = barcodeRenderer.widthMm(serial, settings.getBarcodeModuleMm());
            BigDecimal available = settings.getContentWidthMm()
                    .subtract(settings.getMarginLeftMm().multiply(BigDecimal.valueOf(2)));
            if (barcodeWidth.compareTo(available) > 0) {
                problems.add(new LabelDtos.Problem(id, serial,
                        ("The barcode for %s needs %s mm but only %s mm fits across the tag. "
                                + "Lower the narrow bar width in label settings, or use wider tags.")
                                .formatted(serial, scale(barcodeWidth), scale(available))));
                continue;
            }

            LabelDtos.Label candidate = new LabelDtos.Label(
                    item.getId(),
                    serial,
                    purityText,
                    particulars(item),
                    item.getWeightGrams(),
                    item.getSize(),
                    item.getItemType().getName(),
                    item.getCategory().getName(),
                    item.getSubCategory() == null ? null : item.getSubCategory().getName(),
                    shortName,
                    item.getStatus().name());

            // The head stops at the bottom edge of the tag, so a design that is
            // too tall does not look wrong - it just comes out with its last line
            // or its logo missing. Better to say so than to spoil a tag.
            BigDecimal reach = LabelLayout
                    .of(candidate, settings, barcodeRenderer.modules(serial), shopLogo.isAvailable())
                    .bottomMm();
            if (reach.compareTo(settings.getLabelHeightMm()) > 0) {
                problems.add(new LabelDtos.Problem(id, serial,
                        ("The label for %s is %s mm tall but the tag is only %s mm. "
                                + "Reduce the detail type size or the logo size in label settings.")
                                .formatted(serial, scale(reach), scale(settings.getLabelHeightMm()))));
                continue;
            }

            labels.add(candidate);
        }

        // Two rows sharing a serial number would print two identical barcodes: the
        // database forbids it, but a label is not the place to discover otherwise.
        Map<String, Long> bySerial = labels.stream()
                .collect(Collectors.groupingBy(LabelDtos.Label::serialNumber, Collectors.counting()));
        bySerial.forEach((serial, count) -> {
            if (count > 1) {
                problems.add(new LabelDtos.Problem(null, serial,
                        "Serial number %s appears on %d inventory rows. Fix the duplicate before printing."
                                .formatted(serial, count)));
            }
        });

        int copies = request.copiesOrOne();
        if (copies == 1) {
            return labels;
        }
        List<LabelDtos.Label> repeated = new ArrayList<>(labels.size() * copies);
        for (LabelDtos.Label label : labels) {
            for (int copy = 0; copy < copies; copy++) {
                repeated.add(label);
            }
        }
        return repeated;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    /** What the tag calls the piece: its sub category if it has one, else its category. */
    private static String particulars(InventoryItem item) {
        return item.getSubCategory() != null ? item.getSubCategory().getName() : item.getCategory().getName();
    }

    private Long recordPrint(List<LabelDtos.Label> labels) {
        LabelPrintJob job = new LabelPrintJob();
        job.setPrintedAt(Instant.now());
        job.setPrintedBy(SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
        job.setLabelCount(labels.size());
        job.setSerialNumbers(labels.stream().map(LabelDtos.Label::serialNumber).collect(Collectors.joining(",")));
        printJobRepository.save(job);
        log.info("{} label(s) printed by {}: {}", labels.size(), job.getPrintedBy(), job.getSerialNumbers());
        return job.getId();
    }

    private LabelSettings requireSettings() {
        return settingsRepository
                .findById(LabelSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Label settings are missing. Flyway migration V12 has not been applied."));
    }

    private static LabelDtos.Settings toDto(LabelSettings settings) {
        return new LabelDtos.Settings(
                settings.getShopShortName(),
                settings.getLabelWidthMm(),
                settings.getLabelHeightMm(),
                settings.getLabelsAcross(),
                settings.getContentWidthMm(),
                settings.getContentHeightMm(),
                settings.getMarginTopMm(),
                settings.getMarginLeftMm(),
                settings.getOffsetXMm(),
                settings.getOffsetYMm(),
                settings.getRotationDegrees(),
                settings.getBarcodeHeightMm(),
                settings.getBarcodeModuleMm(),
                settings.getSerialFontPt(),
                settings.getPurityFontPt(),
                settings.getShopFontPt(),
                settings.getShopMark() == null ? ShopMark.TEXT.name() : settings.getShopMark().name(),
                settings.getShopLogoHeightMm(),
                settings.getDetailFontPt(),
                settings.getPrinterName(),
                settings.getPrintMode() == null ? PrintMode.DIRECT.name() : settings.getPrintMode().name(),
                settings.isShowPurity(),
                BARCODE_TYPE);
    }
}
