package com.jewellery.erp.labels.service;

import com.google.zxing.oned.Code128Writer;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Renders a Code 128 barcode as SVG.
 *
 * <p>SVG rather than a raster image on purpose: a thermal printer prints what
 * the Windows driver rasterises, and a vector bar lands on exact dot boundaries
 * at whatever the printer's resolution is. A PNG scaled to millimetres blurs the
 * bar edges, and a blurred Code 128 is the usual reason a scanner refuses a
 * jewellery tag.
 *
 * <p>The encoding itself comes from ZXing, the same library used to decode
 * barcodes, so the bars are checked against a reader in the tests rather than
 * trusted.
 */
@Component
public class Code128Renderer {

    private final Code128Writer writer = new Code128Writer();

    /** Width in millimetres, and the SVG markup, for one barcode. */
    public record Barcode(String value, BigDecimal widthMm, BigDecimal heightMm, String svg) {}

    /**
     * @param value what the scanner will read back - for a label, the piece's
     *     serial number and nothing else
     * @param moduleMm width of the narrowest bar
     */
    public Barcode render(String value, BigDecimal moduleMm, BigDecimal heightMm) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "serialNumber",
                    "A barcode needs a serial number.");
        }
        boolean[] modules = writer.encode(value);

        BigDecimal width = moduleMm.multiply(BigDecimal.valueOf(modules.length));
        StringBuilder bars = new StringBuilder();
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
            // One rect per run of dark modules, not per module: fewer nodes, and
            // adjacent rects can otherwise show hairline seams when rasterised.
            bars.append("<rect x=\"%s\" y=\"0\" width=\"%s\" height=\"%s\"/>".formatted(
                    mm(moduleMm.multiply(BigDecimal.valueOf(start))),
                    mm(moduleMm.multiply(BigDecimal.valueOf(index - start))),
                    mm(heightMm)));
        }

        String svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%smm\" height=\"%smm\" "
                + "viewBox=\"0 0 %s %s\" shape-rendering=\"crispEdges\" fill=\"#000\" "
                + "preserveAspectRatio=\"none\" role=\"img\" aria-label=\"Barcode %s\">%s</svg>")
                .formatted(mm(width), mm(heightMm), mm(width), mm(heightMm), escape(value), bars);
        return new Barcode(value, width, heightMm, svg);
    }

    /** The bar pattern itself: what both renderers draw from. */
    public boolean[] modules(String value) {
        requireValue(value);
        return writer.encode(value);
    }

    /** SVG for a pattern already encoded, at the given bar width and height. */
    public String svg(boolean[] modules, BigDecimal moduleMm, BigDecimal heightMm, String value) {
        BigDecimal width = moduleMm.multiply(BigDecimal.valueOf(modules.length));
        StringBuilder bars = new StringBuilder();
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
            bars.append("<rect x=\"%s\" y=\"0\" width=\"%s\" height=\"%s\"/>".formatted(
                    mm(moduleMm.multiply(BigDecimal.valueOf(start))),
                    mm(moduleMm.multiply(BigDecimal.valueOf(index - start))),
                    mm(heightMm)));
        }
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%smm\" height=\"%smm\" "
                + "viewBox=\"0 0 %s %s\" shape-rendering=\"crispEdges\" fill=\"#000\" "
                + "preserveAspectRatio=\"none\" role=\"img\" aria-label=\"Barcode %s\">%s</svg>")
                .formatted(mm(width), mm(heightMm), mm(width), mm(heightMm), escape(value), bars);
    }

    private static void requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "serialNumber",
                    "A barcode needs a serial number.");
        }
    }

    /** Printed width of a barcode, without building it - for the "does it fit?" check. */
    public BigDecimal widthMm(String value, BigDecimal moduleMm) {
        return moduleMm.multiply(BigDecimal.valueOf(writer.encode(value).length));
    }

    private static String mm(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
