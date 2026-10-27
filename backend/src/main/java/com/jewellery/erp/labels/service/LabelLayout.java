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
 *   T                SJ
 *   K  ||||||||||     WOMENS RING
 *   S  905351 22K/916 GMS.20.800
 * </pre>
 *
 * The side mark stands on its side in the strip before the bars, the serial
 * number (and, for gold, the purity after it) reads underneath them, and the
 * short name heads the name / weight / size block on the right - or below, when
 * the tag is too narrow for both side by side. Shrinking the bars to make room
 * is how a barcode stops scanning, so the layout never does that.
 */
public record LabelLayout(
        BigDecimal widthMm, BigDecimal heightMm, List<Element> elements, boolean sideBySide) {

    /** A piece of the label, positioned by its top-left corner. */
    public sealed interface Element permits Bars, Text, VerticalText, Logo {}

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

    /**
     * Text turned a quarter turn, reading upwards.
     *
     * <p>Positioned by the top-left of the box it ends up occupying, like every
     * other element, so a renderer does not have to know which way it was
     * turned to place it. Its box is as wide as the font's line height and as
     * tall as the string is long - the opposite way round from {@link Text}.
     */
    public record VerticalText(String value, BigDecimal xMm, BigDecimal yMm, BigDecimal fontPt, boolean bold)
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
    /**
     * The side mark's own nudge: 5 px right and 5 px down, at the 96 dpi a
     * browser calls a pixel.
     *
     * <p>From the shop's printed sheet. Hard against the leading edge the mark
     * came out crowding the tag's border, and a hair high against the bars
     * beside it. The indent is taken before the mark rather than out of the
     * gap after it, so the bars keep their clear space.
     */
    private static final BigDecimal SIDE_MARK_INDENT_MM = new BigDecimal("1.323");

    private static final BigDecimal SIDE_MARK_DROP_MM = new BigDecimal("1.323");

    /**
     * How small the description may be set before it is no better than a smudge.
     *
     * <p>A thermal head at this size is already laying down four or five dots
     * per stroke; below this the letters close up and the line reads as a grey
     * bar. A description too long even at this size overflows rather than
     * disappearing - an unreadable word is still worth more than no word.
     */
    private static final BigDecimal MIN_NAME_FONT_PT = new BigDecimal("3.0");

    /**
     * How much smaller the name may be set to keep it on one line.
     *
     * <p>A shade, not a squeeze. Past this the line is better wrapped than
     * shrunk - and a name set at half the weight below it looks like a
     * mistake rather than a decision.
     */
    private static final BigDecimal SLIGHT_SHRINK = new BigDecimal("0.80");

    /** The step the name's type is tried in when a wrap has to be sized. */
    private static final BigDecimal FONT_STEP_PT = new BigDecimal("0.1");

    /** A name is a name, not a paragraph; past this the tag is the wrong size. */
    private static final int MAX_NAME_LINES = 3;

    /**
     * Between the split line and the first letter after it.
     *
     * <p>Enough that the description does not sit on the cut, and no more:
     * the right panel is narrow and every millimetre spent here is one the
     * name does not have. Half a millimetre is four dots at 203 dpi.
     */
    private static final BigDecimal SPLIT_GAP_MM = new BigDecimal("0.5");

    /** Code 128's own quiet zone: ten narrow bars, either side of the symbol. */
    private static final BigDecimal QUIET_ZONE_MODULES = BigDecimal.TEN;

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
        // The short name heads the detail block; the strip before the bars
        // belongs to the side mark.
        //
        // A tag carries one shop mark, not two: where the logo is drawn it is
        // the mark, and printing the name above it as well would both repeat
        // the shop and cost the detail column a line it has not got on a tag
        // 12 mm deep. The name still appears when a logo was asked for but
        // cannot be drawn, which is the existing fallback.
        String shop = mark == ShopMark.NONE || useLogo || label.shopShortName() == null
                ? ""
                : label.shopShortName().trim();
        String sideMark = settings.getSideMarkText() == null ? "" : settings.getSideMarkText().trim();
        String serial = label.serialNumber();
        String purity = settings.isShowPurity() ? label.purityText() : null;
        List<String> details = detailLines(label);

        // The logo box is square, so it is as wide as it is tall.
        BigDecimal logoSize = useLogo ? settings.getShopLogoHeightMm() : BigDecimal.ZERO;
        // Turned on its side, so its box is the other way round: as wide as one
        // line is tall, and as tall as the string is long.
        BigDecimal sideW = sideMark.isEmpty() ? BigDecimal.ZERO : lineHeightMm(settings.getShopFontPt());
        BigDecimal sideH = sideMark.isEmpty()
                ? BigDecimal.ZERO
                : textWidthMm(sideMark, settings.getShopFontPt());
        BigDecimal serialW = textWidthMm(serial, settings.getSerialFontPt());
        BigDecimal purityW = purity == null ? BigDecimal.ZERO : textWidthMm(purity, settings.getPurityFontPt());
        BigDecimal identW = purity == null ? serialW : serialW.add(IDENT_GAP_MM).add(purityW);
        BigDecimal identH = lineHeightMm(settings.getSerialFontPt().max(settings.getPurityFontPt()));

        BigDecimal barsColumnW = barcodeW.max(identW);
        BigDecimal contentW = settings.getContentWidthMm().subtract(settings.getMarginLeftMm().multiply(TWO));
        BigDecimal contentH = settings.getContentHeightMm().subtract(settings.getMarginTopMm().multiply(TWO));

        // The side mark takes the strip before the bars when the left panel
        // has room for both, and is dropped when it has not. Narrowing the
        // bars to make it fit is how a barcode stops scanning, so that is
        // never an option - and the panel, not the whole tag, is what they
        // have to share, because the bars stop at the split line.
        BigDecimal sideStripW = SIDE_MARK_INDENT_MM.add(sideW).add(INNER_GAP_MM);
        // Against the panel itself, not the room the bars get. The quiet zone
        // is clear space a scanner needs around the bars; the serial reading
        // underneath them may sit closer to the cut than that, and often is
        // the wider of the two once a purity follows it.
        boolean hasSideMark = !sideMark.isEmpty()
                && sideStripW.add(barsColumnW).compareTo(splitLineMm(settings)) <= 0;
        BigDecimal codeH = barcodeH.add(IDENT_TOP_MM).add(identH);

        BigDecimal originX = settings.getMarginLeftMm();

        // Where the detail column starts: just past the tag's split line.
        //
        // The tag is die-cut into two panels and the description belongs to
        // the right one, so it begins a whisker after the cut rather than
        // wherever the bars happen to end. That also settles a second thing:
        // a barcode's width follows what the serial encodes to, not how long
        // it looks - 151 is three digits and takes 68 modules, because an odd
        // digit count forces a code switch, where the four digits of 3130
        // take 57 - and measuring from the cut keeps every tag in a tray
        // reading as one column whatever its serial came to.
        BigDecimal tagLeft = tagLeftMm(settings);
        BigDecimal tagRight = tagLeft.add(settings.getLabelWidthMm());
        BigDecimal detailColumnX = tagLeft.add(splitLineMm(settings)).add(SPLIT_GAP_MM);
        BigDecimal detailRoomW = tagRight.subtract(detailColumnX).max(BigDecimal.ZERO);

        BigDecimal detailLineH = lineHeightMm(settings.getDetailFontPt());

        // The description gets the rest of the tag, and is made to stay on it.
        //
        // The weight and the size are of a known length. The name is whatever
        // the piece is called - it is the sub category's name - and a long one
        // ran off the end of the tag and printed onto the backing. So it is
        // set a little smaller to keep it on one line, and if that is not
        // enough it is wrapped onto the next. Wrapping at the size asked for
        // beats squeezing it onto one line at half of it: two readable lines
        // are worth more to a jeweller than one unreadable one.
        //
        // How many lines the name may have is whatever the column has spare
        // after the weight and the size. The shop's mark is not counted
        // against it: a mark is the least of what a tag carries, so where the
        // two cannot both be had the mark gives way - which the shop line's
        // own test below already does, once it sees how tall the name came
        // out. Counting it here instead cost a long name its last words.
        int roomLines = contentH.divide(detailLineH, 0, RoundingMode.DOWN).intValue();
        int otherLines = Math.max(0, details.size() - 1);
        int maxNameLines = Math.min(MAX_NAME_LINES, Math.max(1, roomLines - otherLines));
        FittedName fittedName = fitName(
                details.isEmpty() ? "" : details.get(0), settings.getDetailFontPt(), detailRoomW, maxNameLines);

        List<DetailLine> detailLines = new ArrayList<>();
        if (!details.isEmpty()) {
            // The description stays the bold one: it is what a jeweller reads
            // first, and the short name above it is the same on every tag in
            // the tray. Every line of a wrapped name is part of that name.
            for (String line : fittedName.lines()) {
                detailLines.add(new DetailLine(line, fittedName.fontPt(), true, Role.NAME));
            }
            for (String line : details.subList(1, details.size())) {
                detailLines.add(new DetailLine(line, settings.getDetailFontPt(), false, Role.DETAIL));
            }
        }

        BigDecimal linesH = detailLineH.multiply(BigDecimal.valueOf(detailLines.size()));
        // The logo is the last thing in the detail column, under the size line.
        BigDecimal logoExtra = useLogo
                ? (details.isEmpty() ? BigDecimal.ZERO : LOGO_TOP_MM).add(logoSize)
                : BigDecimal.ZERO;

        // The short name heads this column in the column's own type size, not
        // its own: it belongs to the block it sits on top of, and the smaller
        // of the two is the one that stands a chance of fitting.
        //
        // And it is only printed when the column still fits the tag with it.
        // A mark is the least of what a tag carries - the barcode, the weight
        // and the size are the point of the thing - so where the two cannot
        // both be had, the mark gives way rather than the print failing. On a
        // 12 mm tag with the detail type at 7 pt, a name, a weight and a size
        // already fill the column; a fourth line pushed it to 12.9 mm and
        // stopped the shop printing at all.
        BigDecimal shopLineH = shop.isEmpty() ? BigDecimal.ZERO : lineHeightMm(settings.getDetailFontPt());
        boolean hasShopLine = !shop.isEmpty()
                && linesH.add(logoExtra).add(shopLineH).compareTo(contentH) <= 0;

        BigDecimal detailTextH = linesH.add(hasShopLine ? shopLineH : BigDecimal.ZERO);
        BigDecimal detailH = detailTextH.add(logoExtra);
        boolean hasDetailBlock = hasShopLine || !details.isEmpty() || useLogo;

        BigDecimal detailW = detailLines.stream()
                .map(line -> textWidthMm(line.text(), line.fontPt()))
                .reduce(BigDecimal.ZERO, BigDecimal::max)
                .max(hasShopLine ? textWidthMm(shop, settings.getDetailFontPt()) : BigDecimal.ZERO)
                .max(logoSize);

        // Side by side when both blocks fit across the tag. When they do not, the
        // details drop below the barcode - but only if the tag is deep enough to
        // hold them. On a tag as shallow as this shop's, stacking would run the
        // last line off the bottom edge and lose it, so the blocks stay side by
        // side and the overflow goes sideways, where it is still readable. Either
        // way the fix is the same: a smaller detail font, or a longer head.
        BigDecimal stackedH = codeH.add(IDENT_TOP_MM).add(detailH);
        boolean sideBySide = !hasDetailBlock
                || detailColumnX.subtract(originX).add(detailW).compareTo(contentW) <= 0
                || stackedH.compareTo(contentH) > 0;
        BigDecimal totalH = sideBySide ? codeH.max(detailH) : stackedH;

        BigDecimal originY = settings.getMarginTopMm()
                .add(contentH.subtract(totalH).max(BigDecimal.ZERO).divide(TWO, 3, RoundingMode.HALF_UP));

        List<Element> elements = new ArrayList<>();

        BigDecimal codeY = sideBySide
                ? originY.add(totalH.subtract(codeH).divide(TWO, 3, RoundingMode.HALF_UP))
                : originY;
        BigDecimal barsColumnX = hasSideMark ? originX.add(sideStripW) : originX;
        BigDecimal barsY = codeY;
        BigDecimal barsX = barsColumnX.add(barsColumnW.subtract(barcodeW).divide(TWO, 3, RoundingMode.HALF_UP));

        if (hasSideMark) {
            // Centred against the bars, which is the tallest thing beside it,
            // then nudged down by the shop's 5 px.
            BigDecimal sideY = barsY
                    .add(barcodeH.subtract(sideH).divide(TWO, 3, RoundingMode.HALF_UP))
                    .add(SIDE_MARK_DROP_MM);
            elements.add(new VerticalText(
                    sideMark, originX.add(SIDE_MARK_INDENT_MM), sideY, settings.getShopFontPt(), true));
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
            BigDecimal detailX = sideBySide ? detailColumnX : originX;
            BigDecimal detailY = sideBySide
                    ? originY.add(totalH.subtract(detailH).divide(TWO, 3, RoundingMode.HALF_UP))
                    : codeY.add(codeH).add(IDENT_TOP_MM);
            BigDecimal lineY = detailY;
            if (hasShopLine) {
                elements.add(new Text(shop, detailX, lineY, settings.getDetailFontPt(), true, Role.SHOP));
                lineY = lineY.add(shopLineH);
            }
            for (int line = 0; line < detailLines.size(); line++) {
                DetailLine detail = detailLines.get(line);
                elements.add(new Text(detail.text(), detailX,
                        lineY.add(detailLineH.multiply(BigDecimal.valueOf(line))),
                        detail.fontPt(), detail.bold(), detail.role()));
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
                // Turned on its side, so its depth is the length of the string.
                case VerticalText side -> side.yMm().add(textWidthMm(side.value(), side.fontPt()));
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

    /**
     * Where the tag's split line falls, from its left edge.
     *
     * <p>Unset until the shop measures its own stock, and the middle of the
     * tag until then - which is where the layout assumed it was.
     */
    public static BigDecimal splitLineMm(LabelSettings settings) {
        BigDecimal split = settings.getSplitLineMm();
        return split == null || split.signum() <= 0
                ? settings.getLabelWidthMm().divide(TWO, 3, RoundingMode.HALF_UP)
                : split;
    }

    /**
     * How much of the tag the bars may take.
     *
     * <p>Everything up to the split line, less the strip the side mark stands
     * in and the quiet zone the symbology needs. A bar printed across a cut
     * is a bar a scanner reads as two, so nothing is allowed over the line -
     * and nothing is allowed within a quiet zone of it either, because a
     * scanner needs to see clear tag before the first bar and after the last.
     */
    public static BigDecimal barcodeRoomMm(LabelSettings settings) {
        BigDecimal room = splitLineMm(settings).subtract(quietZoneMm(settings).multiply(TWO));
        String sideMark = settings.getSideMarkText() == null ? "" : settings.getSideMarkText().trim();
        if (!sideMark.isEmpty()) {
            room = room.subtract(SIDE_MARK_INDENT_MM)
                    .subtract(lineHeightMm(settings.getShopFontPt()))
                    .subtract(INNER_GAP_MM);
        }
        return room.max(BigDecimal.ZERO);
    }

    /**
     * The clear space Code 128 wants either side of the bars.
     *
     * <p>Ten times the narrow bar, which is the symbology's own figure. At
     * the shop's 0.25 mm bar that is 2.5 mm.
     */
    public static BigDecimal quietZoneMm(LabelSettings settings) {
        return settings.getBarcodeModuleMm().multiply(QUIET_ZONE_MODULES);
    }

    /** One line of the detail column, with the type it is set in. */
    private record DetailLine(String text, BigDecimal fontPt, boolean bold, Role role) {}

    /** A name made to fit: the lines to print, and the type to print them in. */
    private record FittedName(List<String> lines, BigDecimal fontPt) {}

    /**
     * Fits the name to the column: a little smaller first, then wrapped.
     *
     * <p>In that order because one line reads better than two. A name only a
     * shade too long is set a shade smaller and stays on its line; one that is
     * properly too long is wrapped at the size asked for, since two readable
     * lines beat one squeezed one.
     */
    private static FittedName fitName(String name, BigDecimal fontPt, BigDecimal roomMm, int maxLines) {
        if (name.isEmpty() || roomMm.signum() <= 0 || textWidthMm(name, fontPt).compareTo(roomMm) <= 0) {
            return new FittedName(List.of(name), fontPt);
        }
        BigDecimal slight = fontPt.multiply(SLIGHT_SHRINK).setScale(2, RoundingMode.DOWN).max(MIN_NAME_FONT_PT);
        if (textWidthMm(name, slight).compareTo(roomMm) <= 0) {
            return new FittedName(List.of(name), slight);
        }
        for (BigDecimal size = fontPt; size.compareTo(MIN_NAME_FONT_PT) >= 0; size = size.subtract(FONT_STEP_PT)) {
            List<String> lines = wrap(name, size, roomMm);
            if (lines.size() <= maxLines) {
                return new FittedName(lines, size);
            }
        }
        // Out of room and out of sizes. Two lines at the smallest type hold
        // around a hundred characters between them, so nothing the shop calls
        // a sub category reaches here; if one ever does, the tag keeps what
        // fits rather than printing the rest onto the backing.
        List<String> lines = wrap(name, MIN_NAME_FONT_PT, roomMm);
        return new FittedName(lines.subList(0, Math.min(lines.size(), maxLines)), MIN_NAME_FONT_PT);
    }

    /**
     * Breaks text at the spaces to fit a width.
     *
     * <p>A single word wider than the column is broken mid-word. It reads
     * badly, but a word that cannot be broken cannot be kept on the tag, and
     * staying on the tag is the point.
     */
    private static List<String> wrap(String text, BigDecimal fontPt, BigDecimal roomMm) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            while (textWidthMm(word, fontPt).compareTo(roomMm) > 0 && word.length() > 1) {
                int fits = Math.max(1, roomMm.divide(
                        textWidthMm("x", fontPt), 0, RoundingMode.DOWN).intValue());
                if (fits >= word.length()) {
                    break;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, fits));
                word = word.substring(fits);
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || textWidthMm(candidate, fontPt).compareTo(roomMm) <= 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    /**
     * The tag's own left edge, in the same millimetres the elements are placed in.
     *
     * <p>Not always the origin. A negative left margin is how the shop undoes a
     * margin its printer adds of its own accord: the artwork starts 20 mm before
     * the tag, the page is grown by that much so nothing is clipped, and the
     * roll still begins where it always did. The origin is therefore off the
     * front of the tag, and anything measured against the tag - its middle, for
     * one - has to start from here instead.
     */
    private static BigDecimal tagLeftMm(LabelSettings settings) {
        // The same sum {@code LabelPrinterService.leadMm} does, read backwards:
        // where the page was grown to the left, the artwork's own origin is the
        // tag's edge; where it was not, the print offset alone separates them.
        BigDecimal shift = settings.getMarginLeftMm().add(settings.getOffsetXMm());
        return shift.signum() < 0 ? settings.getMarginLeftMm() : settings.getOffsetXMm().negate();
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
