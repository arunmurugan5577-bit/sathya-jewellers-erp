package com.jewellery.erp.labels.service;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.entity.LabelSettings;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends labels straight to the label printer - no browser, no print dialog.
 *
 * <p>The application runs on the shop's own PC, so the Windows printer the TVS
 * driver installs is visible to this process. Printing from here rather than
 * from the browser is what makes the page come out at 60 x 12 mm instead of
 * being laid on A4: the paper size is set on the job itself, so nothing depends
 * on whoever last changed a setting in a print dialog.
 *
 * <p>Drawing is Java2D at the coordinates {@link LabelLayout} works out - the
 * same numbers the on-screen preview uses, so the preview is the print.
 */
@Component
public class LabelPrinterService {

    private static final Logger log = LoggerFactory.getLogger(LabelPrinterService.class);
    /** Java2D user space is 1/72 inch, which is also what a font point is. */
    private static final double POINTS_PER_MM = 72d / 25.4d;
    /** The head on the TVS LP 46, and every other label printer worth the name. */
    private static final double PRINT_DPI = 203d;
    /** Grey darker than this burns a dot; lighter leaves the tag blank. */
    private static final int BURN_BELOW = 150;
    private static final String FONT_FAMILY = "Arial";

    private final Code128Renderer barcodeRenderer;
    private final ShopLogo shopLogo;

    public LabelPrinterService(Code128Renderer barcodeRenderer, ShopLogo shopLogo) {
        this.barcodeRenderer = barcodeRenderer;
        this.shopLogo = shopLogo;
    }

    /** Printers Windows knows about, for the settings screen. */
    public List<String> availablePrinters() {
        return java.util.Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public String defaultPrinter() {
        PrintService service = PrintServiceLookup.lookupDefaultPrintService();
        return service == null ? null : service.getName();
    }

    /**
     * Prints every label, one per page, on the configured printer.
     *
     * @return the printer the job went to
     */
    public String print(List<LabelDtos.Label> labels, LabelSettings settings) {
        String printerName = settings.getPrinterName();
        PrintService service = findService(printerName);

        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(service);
        } catch (PrinterException ex) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "printerName",
                    "Could not open the printer %s. Check that it is switched on and connected."
                            .formatted(service.getName()));
        }

        job.setPrintable(new LabelPrintable(labels, settings), pageFormat(settings));
        job.setJobName("Jewellery labels (%d)".formatted(labels.size()));

        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new JobName("Jewellery labels", null));
        // The printable area is declared so the driver does not reserve margins of
        // its own. No MediaSizeName is asked for: MediaSize.findMedia returns the
        // nearest STANDARD paper, and for a 60 x 12 mm tag that is ISO A10 - a
        // 26 x 37 mm portrait sheet. Naming it made the driver print the label
        // sideways on a page a third of its width, and feed tags to reach it. The
        // Paper on the PageFormat carries the real size instead.
        float widthIn = mmToInches(pageWidthMm(settings));
        float heightIn = mmToInches(settings.getLabelHeightMm());
        attributes.add(new MediaPrintableArea(0f, 0f, widthIn, heightIn, MediaPrintableArea.INCH));
        attributes.add(OrientationRequested.PORTRAIT);

        try {
            job.print(attributes);
        } catch (PrinterException ex) {
            log.warn("Label print job failed on {}", service.getName(), ex);
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "printerName",
                    "The printer %s refused the job: %s".formatted(service.getName(), ex.getMessage()));
        }
        log.info("{} label(s) printed on {}", labels.size(), service.getName());
        return service.getName();
    }

    private PrintService findService(String printerName) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (printerName == null || printerName.isBlank()) {
            PrintService fallback = PrintServiceLookup.lookupDefaultPrintService();
            if (fallback == null) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "printerName",
                        "No printer is set up. Choose the label printer in label settings.");
            }
            return fallback;
        }
        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName.trim())) {
                return service;
            }
        }
        throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "printerName",
                "Printer \"%s\" was not found on this computer. Check it is installed, or choose another in label settings."
                        .formatted(printerName));
    }

    /**
     * How far the artwork reaches back past the start of the tag.
     *
     * <p>A negative left margin or print offset drags the design towards the
     * leading edge, which is how a margin the driver adds of its own accord gets
     * undone. Java clips a page at its edge, so the page is grown by that much
     * on the left instead of the barcode quietly losing its first bars.
     */
    public static BigDecimal leadMm(LabelSettings settings) {
        BigDecimal leftmost = settings.getMarginLeftMm().add(settings.getOffsetXMm());
        return leftmost.signum() < 0 ? leftmost.negate() : BigDecimal.ZERO;
    }

    /** The full width of one printed page: the row of tags, plus any lead-in. */
    public static BigDecimal pageWidthMm(LabelSettings settings) {
        return leadMm(settings).add(settings.getLabelWidthMm()
                .multiply(BigDecimal.valueOf(Math.max(1, settings.getLabelsAcross()))));
    }

    /** A page exactly the size of one row of tags, with no unprintable margin. */
    private static PageFormat pageFormat(LabelSettings settings) {
        double widthPt = mmToPoints(pageWidthMm(settings));
        double heightPt = mmToPoints(settings.getLabelHeightMm());

        Paper paper = new Paper();
        paper.setSize(widthPt, heightPt);
        // The whole label is printable: margins are part of the layout, not the page.
        paper.setImageableArea(0, 0, widthPt, heightPt);

        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(PageFormat.PORTRAIT);
        return format;
    }

    /** Draws the labels; one page each. */
    private final class LabelPrintable implements Printable {

        private final List<LabelDtos.Label> labels;
        private final LabelSettings settings;

        private LabelPrintable(List<LabelDtos.Label> labels, LabelSettings settings) {
            this.labels = labels;
            this.settings = settings;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            int across = Math.max(1, settings.getLabelsAcross());
            int firstOnPage = pageIndex * across;
            if (firstOnPage >= labels.size()) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            // Tag x = 0 sits this far into the page when the artwork reaches back
            // past the tag's start.
            g.translate(mmToPoints(leadMm(settings)), 0);
            g.setColor(Color.BLACK);

            for (int column = 0; column < across && firstOnPage + column < labels.size(); column++) {
                Graphics2D cell = (Graphics2D) g.create();
                cell.translate(mmToPoints(settings.getLabelWidthMm()) * column, 0);
                renderLabel(cell, labels.get(firstOnPage + column), settings);
                cell.dispose();
            }
            return PAGE_EXISTS;
        }

    }

    /**
     * Draws a whole page - one row of tags - to a bitmap at the head's own
     * resolution, for an agent to spool somewhere else.
     *
     * <p>Rendering here rather than at the agent keeps one copy of the layout
     * code, which is the only way the preview and the tag stay the same
     * picture. The agent receives a bitmap and has nothing left to decide.
     *
     * @param pageLabels the tags across this page, in order
     * @return a 1-bit PNG, or empty if it could not be encoded
     */
    public Optional<byte[]> renderPagePng(List<LabelDtos.Label> pageLabels, LabelSettings settings) {
        double widthMm = pageWidthMm(settings).doubleValue();
        double heightMm = settings.getLabelHeightMm().doubleValue();
        int width = Math.max(1, (int) Math.round(widthMm / 25.4 * PRINT_DPI));
        int height = Math.max(1, (int) Math.round(heightMm / 25.4 * PRINT_DPI));

        // Drawn in greyscale and thresholded rather than straight into a 1-bit
        // image: Java2D will not antialias text into a bilevel raster, and
        // unantialiased 4 pt type at this size is unreadable.
        BufferedImage grey = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grey.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double scale = PRINT_DPI / 72d;
        g.scale(scale, scale);
        g.translate(mmToPoints(leadMm(settings)), 0);
        g.setColor(Color.BLACK);
        for (int column = 0; column < pageLabels.size(); column++) {
            Graphics2D cell = (Graphics2D) g.create();
            cell.translate(mmToPoints(settings.getLabelWidthMm()) * column, 0);
            renderLabel(cell, pageLabels.get(column), settings);
            cell.dispose();
        }
        g.dispose();

        BufferedImage bilevel = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int lum = grey.getRaster().getSample(x, y, 0);
                bilevel.setRGB(x, y, lum < BURN_BELOW ? 0x000000 : 0xFFFFFF);
            }
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(bilevel, "png", out);
            return Optional.of(out.toByteArray());
        } catch (IOException ex) {
            log.warn("Could not encode a label page for the print agent", ex);
            return Optional.empty();
        }
    }

    /**
     * Draws one label at its top-left corner, in points.
     *
     * <p>Public so a test can render exactly what the printer is handed - the only
     * way to see a thermal label without feeding a tag through the machine.
     */
    public void renderLabel(Graphics2D g, LabelDtos.Label label, LabelSettings settings) {
        LabelLayout layout = LabelLayout.of(
                label, settings, barcodeRenderer.modules(label.serialNumber()), shopLogo.isAvailable());

        Graphics2D cell = (Graphics2D) g.create();
        cell.setColor(Color.BLACK);
        // Print offset and rotation, about the middle of the tag - the same
        // adjustment the preview applies.
        cell.translate(mmToPoints(settings.getOffsetXMm()), mmToPoints(settings.getOffsetYMm()));
        if (settings.getRotationDegrees() != 0) {
            cell.rotate(Math.toRadians(settings.getRotationDegrees()),
                    mmToPoints(settings.getLabelWidthMm()) / 2,
                    mmToPoints(settings.getContentHeightMm()) / 2);
        }

        for (LabelLayout.Element element : layout.elements()) {
            if (element instanceof LabelLayout.Bars bars) {
                drawBars(cell, bars);
            } else if (element instanceof LabelLayout.Text text) {
                drawText(cell, text);
            } else if (element instanceof LabelLayout.Logo logo) {
                drawLogo(cell, logo);
            }
        }
        cell.dispose();
    }

    private static void drawBars(Graphics2D g, LabelLayout.Bars bars) {
        double x = mmToPoints(bars.xMm());
        double y = mmToPoints(bars.yMm());
        double module = mmToPoints(bars.moduleMm());
        double height = mmToPoints(bars.heightMm());

        boolean[] modules = bars.modules();
        int index = 0;
        while (index < modules.length) {
            if (!modules[index]) {
                index++;
                continue;
            }
            int start = index;
            while (index < modules.length && modules[index]) {
                index++;
            }
            // One rectangle per run of dark modules: adjacent fills can otherwise
            // leave hairline seams once the driver rasterises them.
            g.fill(new Rectangle2D.Double(x + start * module, y, (index - start) * module, height));
        }
    }

    private void drawLogo(Graphics2D g, LabelLayout.Logo logo) {
        shopLogo.forHeight(logo.sizeMm()).ifPresent(image -> {
            double x = mmToPoints(logo.xMm());
            double y = mmToPoints(logo.yMm());
            double side = mmToPoints(logo.sizeMm());
            // Nearest neighbour: the bitmap was already reduced to the dot count
            // it prints at, so smoothing here would only blur it back out.
            Graphics2D box = (Graphics2D) g.create();
            box.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            box.drawImage(image, (int) Math.round(x), (int) Math.round(y),
                    (int) Math.round(side), (int) Math.round(side), null);
            box.dispose();
        });
    }

    private static void drawText(Graphics2D g, LabelLayout.Text text) {
        Font font = new Font(FONT_FAMILY, text.bold() ? Font.BOLD : Font.PLAIN, 10)
                .deriveFont(text.fontPt().floatValue());
        g.setFont(font);
        // The layout gives the top of the line; Java2D draws from the baseline.
        float baseline = (float) (mmToPoints(text.yMm()) + g.getFontMetrics().getAscent());
        g.drawString(text.value(), (float) mmToPoints(text.xMm()), baseline);
    }

    private static double mmToPoints(BigDecimal mm) {
        return mm.doubleValue() * POINTS_PER_MM;
    }

    private static float mmToInches(BigDecimal mm) {
        return mm.divide(BigDecimal.valueOf(25.4), 4, RoundingMode.HALF_UP).floatValue();
    }
}
