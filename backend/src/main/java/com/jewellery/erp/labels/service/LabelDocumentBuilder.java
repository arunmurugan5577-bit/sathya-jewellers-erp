package com.jewellery.erp.labels.service;

import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.entity.LabelSettings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The label document for the browser: the on-screen preview, and the fallback
 * for printing when no printer is configured on the server.
 *
 * <p>Positions come from {@link LabelLayout} - the same numbers
 * {@link LabelPrinterService} draws with - so what the preview shows is what the
 * printer puts on the tag. Each element is placed absolutely in millimetres
 * rather than flowed, because flowing text lays out differently in a browser
 * than in Java2D and the two would drift apart.
 *
 * <p>Direct printing is the normal path; this HTML exists so the shop can still
 * see a label, and still print from a machine that has no printer set up.
 */
@Component
public class LabelDocumentBuilder {

    private final Code128Renderer barcodeRenderer;
    private final ShopLogo shopLogo;

    public LabelDocumentBuilder(Code128Renderer barcodeRenderer, ShopLogo shopLogo) {
        this.barcodeRenderer = barcodeRenderer;
        this.shopLogo = shopLogo;
    }

    /**
     * @param labels in print order, filling each row left to right
     * @param startColumn 1-based tag to begin at, so a row with tags already used
     *     can be finished off rather than thrown away
     * @param screenPreview outlines the tag so its edges are visible on screen
     */
    public String build(
            List<LabelDtos.Label> labels, LabelSettings settings, int startColumn, boolean screenPreview) {

        int across = Math.max(1, settings.getLabelsAcross());
        // The printer grows the page to the left when the artwork reaches back
        // past the start of the tag; the preview has to do the same or it stops
        // being a preview of the print.
        BigDecimal lead = LabelPrinterService.leadMm(settings);
        BigDecimal pageWidth = lead.add(settings.getLabelWidthMm().multiply(BigDecimal.valueOf(across)));

        StringBuilder rows = new StringBuilder();
        int column = Math.min(Math.max(startColumn, 1), across) - 1;
        StringBuilder cells = new StringBuilder();
        for (int i = 0; i < column; i++) {
            cells.append("<div class=\"cell\"></div>"); // tags already used on this row
        }
        for (LabelDtos.Label label : labels) {
            cells.append(cell(label, settings));
            if (++column == across) {
                rows.append(row(cells.toString()));
                cells.setLength(0);
                column = 0;
            }
        }
        if (column > 0) {
            for (int i = column; i < across; i++) {
                cells.append("<div class=\"cell\"></div>"); // tags left over on the last row
            }
            rows.append(row(cells.toString()));
        }

        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>Labels</title><style>
                @page { size: %s %s; margin: 0; }
                html, body { margin: 0; padding: 0; background: #fff; }
                .row {
                  width: %s; height: %s;
                  padding-left: %s; box-sizing: border-box;
                  display: flex; align-items: flex-start;
                  overflow: hidden;
                  page-break-after: always; break-after: page;
                  font-family: Arial, "Helvetica Neue", Helvetica, sans-serif;
                  color: #000;
                }
                .row:last-child { page-break-after: auto; break-after: auto; }
                .cell {
                  position: relative;
                  width: %s; height: %s;
                  overflow: hidden;
                }
                .cell > .inner {
                  position: absolute; inset: 0;
                  transform: translate(%s, %s) rotate(%ddeg);
                  transform-origin: %s %s;
                }
                .el { position: absolute; white-space: nowrap; line-height: 1.2; }
                /* Crisp, not smoothed: the bitmap is already at its printed dot count. */
                .el--logo { image-rendering: pixelated; }
                .el--bold { font-weight: 700; }
                .el svg { display: block; }
                %s
                </style></head><body>%s</body></html>
                """
                .formatted(
                        mm(pageWidth), mm(settings.getLabelHeightMm()),
                        mm(pageWidth), mm(settings.getLabelHeightMm()), mm(lead),
                        mm(settings.getLabelWidthMm()), mm(settings.getLabelHeightMm()),
                        mm(settings.getOffsetXMm()), mm(settings.getOffsetYMm()), settings.getRotationDegrees(),
                        mm(settings.getLabelWidthMm().divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP)),
                        mm(settings.getContentHeightMm().divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP)),
                        screenPreview ? PREVIEW_CSS : "",
                        rows);
    }

    /**
     * On-screen only: outlines the tag. An outline rather than a border so the
     * preview stays exactly label-sized - a border would add its own width and
     * put a scrollbar in the preview.
     */
    private static final String PREVIEW_CSS = """
            @media screen {
              html, body { margin: 0; padding: 0; overflow: hidden; }
              .cell { outline: 1px dashed #9a8f7d; outline-offset: -1px; }
            }""";

    private static String row(String cells) {
        return "<section class=\"row\">%s</section>".formatted(cells);
    }

    private String cell(LabelDtos.Label label, LabelSettings settings) {
        LabelLayout layout = LabelLayout.of(
                label, settings, barcodeRenderer.modules(label.serialNumber()), shopLogo.isAvailable());

        StringBuilder body = new StringBuilder();
        for (LabelLayout.Element element : layout.elements()) {
            if (element instanceof LabelLayout.Bars bars) {
                body.append("<div class=\"el\" style=\"left:%s;top:%s\">%s</div>".formatted(
                        mm(bars.xMm()), mm(bars.yMm()),
                        barcodeRenderer.svg(bars.modules(), bars.moduleMm(), bars.heightMm(), label.serialNumber())));
            } else if (element instanceof LabelLayout.Text text) {
                body.append("<div class=\"el%s\" style=\"left:%s;top:%s;font-size:%spt\">%s</div>".formatted(
                        text.bold() ? " el--bold" : "",
                        mm(text.xMm()), mm(text.yMm()), trim(text.fontPt()), escape(text.value())));
            } else if (element instanceof LabelLayout.Logo logo) {
                // The same black-and-white bitmap the printer is handed, so the
                // preview shows the blockiness the tag will actually have.
                shopLogo.dataUri(logo.sizeMm()).ifPresent(uri -> body.append(
                        "<img class=\"el el--logo\" alt=\"\" src=\"%s\" style=\"left:%s;top:%s;width:%s;height:%s\">"
                                .formatted(uri, mm(logo.xMm()), mm(logo.yMm()),
                                        mm(logo.sizeMm()), mm(logo.sizeMm()))));
            }
        }
        return "<div class=\"cell\"><div class=\"inner\">%s</div></div>".formatted(body);
    }

    private static String mm(BigDecimal value) {
        return trim(value) + "mm";
    }

    private static String trim(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
