package com.jewellery.erp.labels.service;

import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.entity.LabelSettings;
import com.jewellery.erp.labels.entity.ShopMark;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Where everything sits on a label, in millimetres from its top-left corner.
 *
 * <p>One calculation, two renderers: the on-screen preview draws these positions
 * as HTML and the printer draws them with Java2D. Without a shared layout the
 * two drift apart, and a preview that lies about the print is worse than no
 * preview at all.
 *
 * <pre>
 *   SJ ||||||||||   WOMENS RING
 *      905351 22K / 916  GMS.20.800
 * </pre>
 *
 * The short name sits left of the bars, the serial number (and, for gold, the
 * purity after it) reads underneath, and the name / weight / size block goes to
 * the right - or below, when the tag is too narrow for both side by side.
 * Shrinking the bars to make room is how a barcode stops scanning, so the layout
 * never does that.
 */
public record LabelLayout(
        BigDecimal widthMm, BigDecimal heightMm, List<Element> elements, boolean sideBySide) {

    /** A piece of the label, positioned by its top-left corner. */
    public sealed interface Element permits Bars, Text, Logo {}

    /** The shop logo, in a square box. The bitmap itself is the renderer's business. */
    public record Logo(BigDecimal xMm, BigDecimal yMm, BigDecimal sizeMm) implements Element {}

    public record Bars(boolean[] modules, BigDecimal moduleMm, BigDecimal xMm, BigDecimal yMm, BigDecimal heightMm)
            implements Element {

        public BigDecimal widthMm() {
            return moduleMm.multiply(BigDecimal.valueOf(modules.length));
        }
    }

    /** @param yMm top of the line; renderers add their own ascent to find the baseline */
    public record Text(String value, BigDecimal xMm, BigDecimal yMm, BigDecimal fontPt, boolean bold, Role role)
            implements Element {}

    public enum Role {
        SHOP,
        SERIAL,
        PURITY,
        NAME,
        DETAIL
    }

    static final BigDecimal POINTS_PER_MM = new BigDecimal("2.835");
    /** Between the short name and the bars. */
    private static final BigDecimal INNER_GAP_MM = new BigDecimal("1.2");
    /** Between the barcode block and the details block. */
    private static final BigDecimal BLOCK_GAP_MM = new BigDecimal("3");
    /** Between the serial number and the purity. */
    private static final BigDecimal IDENT_GAP_MM = new BigDecimal("1");
    /** Under the bars, before the serial number. */
    private static final BigDecimal IDENT_TOP_MM = new BigDecimal("0.4");
    /** Between the last line of the detail block and the logo under it. */
    private static final BigDecimal LOGO_TOP_MM = new BigDecimal("0.5");
    private static final BigDecimal LINE_SPACING = new BigDecimal("1.2");
    /** Arial digits and capitals average a little over half the point size. */
    private static final BigDecimal AVERAGE_CHARACTER_WIDTH = new BigDecimal("0.58");

    public static LabelLayout of(LabelDtos.Label label, LabelSettings settings, boolean[] barcodeModules) {
        return of(label, settings, barcodeModules, settings.getShopMark() == ShopMark.LOGO);
    }

    /**
     * @param logoAvailable whether the renderer actually has logo artwork to
     *     draw. When the setting asks for a logo and there is none, the short
     *     name is used instead rather than leaving a hole on the tag.
     */
    public static LabelLayout of(
            LabelDtos.Label label, LabelSettings settings, boolean[] barcodeModules, boolean logoAvailable) {
        BigDecimal moduleMm = settings.getBarcodeModuleMm();
        BigDecimal barcodeW = moduleMm.multiply(BigDecimal.valueOf(barcodeModules.length));
        BigDecimal barcodeH = settings.getBarcodeHeightMm();

        ShopMark mark = settings.getShopMark() == null ? ShopMark.TEXT : settings.getShopMark();
        // A logo with no size cannot be drawn; fall back rather than fail.
        boolean useLogo = mark == ShopMark.LOGO && logoAvailable && settings.getShopLogoHeightMm() != null;
        // The short name keeps its own place to the left of the bars. The logo,
        // when there is one, goes under the detail block rather than competing
        // with the name for that slot.
        String shop = mark == ShopMark.NONE || label.shopShortName() == null
                ? ""
                : label.shopShortName().trim();
        String serial = label.serialNumber();
        String purity = settings.isShowPurity() ? label.purityText() : null;
        List<String> details = detailLines(label);

        // The logo box is square, so it is as wide as it is tall.
        BigDecimal logoSize = useLogo ? settings.getShopLogoHeightMm() : BigDecimal.ZERO;
        BigDecimal shopW = shop.isEmpty() ? BigDecimal.ZERO : textWidthMm(shop, settings.getShopFontPt());
        BigDecimal serialW = textWidthMm(serial, settings.getSerialFontPt());
        BigDecimal purityW = purity == null ? BigDecimal.ZERO : textWidthMm(purity, settings.getPurityFontPt());
        BigDecimal identW = purity == null ? serialW : serialW.add(IDENT_GAP_MM).add(purityW);
        BigDecimal identH = lineHeightMm(settings.getSerialFontPt().max(settings.getPurityFontPt()));

        BigDecimal barsColumnW = barcodeW.max(identW);
        BigDecimal contentW = settings.getContentWidthMm().subtract(settings.getMarginLeftMm().multiply(TWO));
        BigDecimal contentH = settings.getContentHeightMm().subtract(settings.getMarginTopMm().multiply(TWO));

        // The short name goes beside the bars when there is room, and above them
        // when there is not. Narrowing the bars to make it fit is how a barcode
        // stops scanning, so that is never an option.
        BigDecimal shopH = shop.isEmpty() ? BigDecimal.ZERO : lineHeightMm(settings.getShopFontPt());
        boolean hasMark = !shop.isEmpty();
        boolean shopBeside = hasMark
                && shopW.add(INNER_GAP_MM).add(barsColumnW).compareTo(contentW) <= 0;
        BigDecimal codeW = shopBeside ? shopW.add(INNER_GAP_MM).add(barsColumnW) : barsColumnW.max(shopW);
        BigDecimal codeH = barcodeH.add(IDENT_TOP_MM).add(identH).add(shopBeside ? BigDecimal.ZERO : shopH);

        BigDecimal detailLineH = lineHeightMm(settings.getDetailFontPt());
        BigDecimal detailTextW = details.stream()
                .map(line -> textWidthMm(line, settings.getDetailFontPt()))
                .reduce(BigDecimal.ZERO, BigDecimal::max);
        BigDecimal detailTextH = detailLineH.multiply(BigDecimal.valueOf(details.size()));
        // The logo is the last thing in the detail column, under the size line.
        BigDecimal detailW = detailTextW.max(logoSize);
        BigDecimal detailH = useLogo
                ? detailTextH.add(details.isEmpty() ? BigDecimal.ZERO : LOGO_TOP_MM).add(logoSize)
                : detailTextH;
        boolean hasDetailBlock = !details.isEmpty() || useLogo;

        // Side by side when both blocks fit across the tag. When they do not, the
        // details drop below the barcode - but only if the tag is deep enough to
        // hold them. On a tag as shallow as this shop's, stacking would run the
        // last line off the bottom edge and lose it, so the blocks stay side by
        // side and the overflow goes sideways, where it is still readable. Either
        // way the fix is the same: a smaller detail font, or a longer head.
        BigDecimal stackedH = codeH.add(IDENT_TOP_MM).add(detailH);
        boolean sideBySide = !hasDetailBlock
                || codeW.add(BLOCK_GAP_MM).add(detailW).compareTo(contentW) <= 0
                || stackedH.compareTo(contentH) > 0;
        BigDecimal totalH = sideBySide ? codeH.max(detailH) : stackedH;

        BigDecimal originX = settings.getMarginLeftMm();
        BigDecimal originY = settings.getMarginTopMm()
                .add(contentH.subtract(totalH).max(BigDecimal.ZERO).divide(TWO, 3, RoundingMode.HALF_UP));

        List<Element> elements = new ArrayList<>();

        BigDecimal codeY = sideBySide
                ? originY.add(totalH.subtract(codeH).divide(TWO, 3, RoundingMode.HALF_UP))
                : originY;
        BigDecimal barsColumnX = shopBeside ? originX.add(shopW).add(INNER_GAP_MM) : originX;
        BigDecimal barsY = shopBeside ? codeY : codeY.add(shopH);
        BigDecimal barsX = barsColumnX.add(barsColumnW.subtract(barcodeW).divide(TWO, 3, RoundingMode.HALF_UP));

        if (hasMark) {
            // Beside the bars it is centred against them; above them it takes the
            // first line and the bars move down by its height.
            BigDecimal shopY = shopBeside
                    ? barsY.add(barcodeH.subtract(shopH).divide(TWO, 3, RoundingMode.HALF_UP))
                    : codeY;
            elements.add(new Text(shop, originX, shopY, settings.getShopFontPt(), true, Role.SHOP));
        }
        elements.add(new Bars(barcodeModules, moduleMm, barsX, barsY, barcodeH));

        BigDecimal identX = barsColumnX.add(barsColumnW.subtract(identW).divide(TWO, 3, RoundingMode.HALF_UP));
        BigDecimal identY = barsY.add(barcodeH).add(IDENT_TOP_MM);
        elements.add(new Text(serial, identX, identY, settings.getSerialFontPt(), true, Role.SERIAL));
        if (purity != null) {
            elements.add(new Text(purity, identX.add(serialW).add(IDENT_GAP_MM), identY,
                    settings.getPurityFontPt(), false, Role.PURITY));
        }

        if (hasDetailBlock) {
            BigDecimal detailX = sideBySide ? originX.add(codeW).add(BLOCK_GAP_MM) : originX;
            BigDecimal detailY = sideBySide
                    ? originY.add(totalH.subtract(detailH).divide(TWO, 3, RoundingMode.HALF_UP))
                    : codeY.add(codeH).add(IDENT_TOP_MM);
            for (int line = 0; line < details.size(); line++) {
                elements.add(new Text(details.get(line), detailX,
                        detailY.add(detailLineH.multiply(BigDecimal.valueOf(line))),
                        settings.getDetailFontPt(), line == 0, line == 0 ? Role.NAME : Role.DETAIL));
            }
            if (useLogo) {
                BigDecimal logoY = detailY.add(detailTextH)
                        .add(details.isEmpty() ? BigDecimal.ZERO : LOGO_TOP_MM);
                elements.add(new Logo(detailX, logoY, logoSize));
            }
        }

        return new LabelLayout(settings.getLabelWidthMm(), settings.getContentHeightMm(), elements, sideBySide);
    }

    /**
     * How far down the tag the lowest piece of artwork reaches.
     *
     * <p>Used to refuse a job that would print past the bottom edge. A thermal
     * head simply stops at the edge, so an overflowing label is not obviously
     * wrong on screen - it just comes out with its last line missing.
     */
    public BigDecimal bottomMm() {
        BigDecimal lowest = BigDecimal.ZERO;
        for (Element element : elements) {
            BigDecimal bottom = switch (element) {
                case Bars bars -> bars.yMm().add(bars.heightMm());
                case Text text -> text.yMm().add(lineHeightMm(text.fontPt()));
                case Logo logo -> logo.yMm().add(logo.sizeMm());
            };
            lowest = lowest.max(bottom);
        }
        return lowest;
    }

    /** "WOMENS RING", "GMS.20.800", "Size:11" - only the lines the piece actually has. */
    public static List<String> detailLines(LabelDtos.Label label) {
        List<String> lines = new ArrayList<>(3);
        if (label.particulars() != null && !label.particulars().isBlank()) {
            lines.add(label.particulars().trim().toUpperCase());
        }
        if (label.weightGrams() != null) {
            lines.add("GMS." + label.weightGrams().setScale(3, RoundingMode.HALF_UP).toPlainString());
        }
        if (label.size() != null && !label.size().isBlank()) {
            lines.add("Size:" + label.size().trim());
        }
        return lines;
    }

    public static BigDecimal lineHeightMm(BigDecimal fontPt) {
        return fontPt.multiply(LINE_SPACING).divide(POINTS_PER_MM, 3, RoundingMode.HALF_UP);
    }

    /**
     * Rough printed width of a short piece of text. Rough is enough: it decides
     * whether two blocks fit side by side and centres the serial under the bars,
     * neither of which needs to be exact to the hair.
     */
    public static BigDecimal textWidthMm(String text, BigDecimal fontPt) {
        return fontPt.multiply(AVERAGE_CHARACTER_WIDTH)
                .divide(POINTS_PER_MM, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(text.length()));
    }

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
}
