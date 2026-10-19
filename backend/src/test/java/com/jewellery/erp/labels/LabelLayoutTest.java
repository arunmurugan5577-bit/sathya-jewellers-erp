package com.jewellery.erp.labels;

import static org.assertj.core.api.Assertions.assertThat;

import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.entity.LabelSettings;
import com.jewellery.erp.labels.entity.ShopMark;
import com.jewellery.erp.labels.service.Code128Renderer;
import com.jewellery.erp.labels.service.LabelLayout;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The layout the preview draws and the printer draws. Both read these
 * coordinates, so what is checked here is what lands on the tag.
 */
class LabelLayoutTest {

    private final Code128Renderer renderer = new Code128Renderer();

    /** The shop's tag: 60 x 12 mm, one across. */
    private static LabelLayout.VerticalText sideMark(LabelLayout layout) {
        return layout.elements().stream()
                .filter(LabelLayout.VerticalText.class::isInstance)
                .map(LabelLayout.VerticalText.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no side mark on the tag"));
    }

    private static LabelSettings tagSettings() {
        LabelSettings settings = new LabelSettings();
        settings.setShopShortName("SJ");
        settings.setSideMarkText("TKS");
        settings.setLabelWidthMm(new BigDecimal("60.00"));
        settings.setLabelHeightMm(new BigDecimal("12.00"));
        settings.setLabelsAcross((short) 1);
        settings.setContentWidthMm(new BigDecimal("60.00"));
        settings.setContentHeightMm(new BigDecimal("12.00"));
        settings.setMarginTopMm(new BigDecimal("1.00"));
        settings.setMarginLeftMm(new BigDecimal("1.50"));
        settings.setOffsetXMm(BigDecimal.ZERO);
        settings.setOffsetYMm(BigDecimal.ZERO);
        settings.setRotationDegrees((short) 0);
        settings.setBarcodeHeightMm(new BigDecimal("6.50"));
        settings.setBarcodeModuleMm(new BigDecimal("0.250"));
        settings.setSerialFontPt(new BigDecimal("6.50"));
        settings.setPurityFontPt(new BigDecimal("6.00"));
        settings.setShopFontPt(new BigDecimal("7.00"));
        // These cases are about the text layout; the logo has tests of its own.
        settings.setShopMark(ShopMark.TEXT);
        settings.setShopLogoHeightMm(new BigDecimal("6.50"));
        settings.setDetailFontPt(new BigDecimal("6.00"));
        settings.setShowPurity(true);
        return settings;
    }

    private static LabelDtos.Label gold() {
        return new LabelDtos.Label(1L, "905351", "22K / 916", "Womens Ring", new BigDecimal("20.800"), null,
                "Gold", "Ring", "Womens Ring", "SJ", "AVAILABLE");
    }

    private LabelLayout layoutOf(LabelDtos.Label label, LabelSettings settings) {
        return LabelLayout.of(label, settings, renderer.modules(label.serialNumber()));
    }

    @Test
    @DisplayName("everything stays inside the 60 x 12 mm tag")
    void fitsWithinTheTag() {
        LabelSettings settings = tagSettings();
        LabelLayout layout = layoutOf(gold(), settings);

        for (LabelLayout.Element element : layout.elements()) {
            if (element instanceof LabelLayout.Bars bars) {
                assertThat(bars.xMm()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(bars.xMm().add(bars.widthMm())).isLessThanOrEqualTo(settings.getLabelWidthMm());
                assertThat(bars.yMm().add(bars.heightMm())).isLessThanOrEqualTo(settings.getLabelHeightMm());
            }
            if (element instanceof LabelLayout.Text text) {
                BigDecimal right = text.xMm().add(LabelLayout.textWidthMm(text.value(), text.fontPt()));
                BigDecimal bottom = text.yMm().add(LabelLayout.lineHeightMm(text.fontPt()));
                assertThat(right).as(text.value()).isLessThanOrEqualTo(settings.getLabelWidthMm());
                assertThat(bottom).as(text.value()).isLessThanOrEqualTo(settings.getLabelHeightMm());
            }
        }
    }

    @Test
    @DisplayName("side mark on its side before the bars, short name heading the details after them")
    void followsTheShopsLayout() {
        LabelSettings settings = tagSettings();
        LabelLayout layout = layoutOf(gold(), settings);

        LabelLayout.Bars bars = bars(layout);
        LabelLayout.VerticalText side = sideMark(layout);
        LabelLayout.Text shop = text(layout, LabelLayout.Role.SHOP);
        LabelLayout.Text serial = text(layout, LabelLayout.Role.SERIAL);
        LabelLayout.Text purity = text(layout, LabelLayout.Role.PURITY);
        LabelLayout.Text name = text(layout, LabelLayout.Role.NAME);

        // The strip before the bars belongs to the side mark, standing up.
        assertThat(side.value()).isEqualTo("TKS");
        assertThat(side.xMm()).isEqualByComparingTo(settings.getMarginLeftMm());
        assertThat(side.xMm()).isLessThan(bars.xMm());

        // The serial still reads under the bars, the purity after it.
        assertThat(serial.yMm()).isGreaterThan(bars.yMm().add(bars.heightMm()).subtract(BigDecimal.ONE));
        assertThat(purity.xMm()).isGreaterThan(serial.xMm());

        // The short name has moved to the top of the block after the bars, and
        // the description now sits under it rather than beside the barcode.
        assertThat(shop.value()).isEqualTo("SJ");
        assertThat(shop.xMm()).isGreaterThan(bars.xMm().add(bars.widthMm()));
        assertThat(shop.xMm()).isEqualByComparingTo(name.xMm());
        assertThat(shop.yMm()).isLessThan(name.yMm());

        assertThat(layout.sideBySide()).isTrue();
        assertThat(serial.value()).isEqualTo("905351");
        assertThat(purity.value()).isEqualTo("22K / 916");
    }

    @Test
    @DisplayName("the short name gives way rather than pushing the weight off a full tag")
    void theMarkGivesWayOnAFullTag() {
        // The shop's silver kolusu, at the detail type size the shop actually
        // uses: a long name, a weight and a size already fill the column on a
        // 12 mm tag. Adding a fourth line for the short name took the content
        // to 12.9 mm and stopped the tag printing at all.
        LabelSettings settings = tagSettings();
        settings.setDetailFontPt(new BigDecimal("7.00"));

        LabelDtos.Label kolusu = new LabelDtos.Label(9L, "000001", "80", "Silver T.Kolusu",
                new BigDecimal("36.200"), "5.5\" S TYPE", "Silver", "Silver T.Kolusu",
                "Silver T.Kolusu", "SJ", "AVAILABLE");

        LabelLayout layout = LabelLayout.of(kolusu, settings, renderer.modules("000001"), false);

        assertThat(layout.bottomMm())
                .as("content must stay on the tag")
                .isLessThanOrEqualTo(settings.getLabelHeightMm());
        assertThat(layout.elements())
                .as("the mark is what gives way, not the weight")
                .noneMatch(element -> element instanceof LabelLayout.Text text
                        && text.role() == LabelLayout.Role.SHOP);
        // What the tag is actually for is all still there.
        assertThat(detail(layout, "GMS.36.200")).isNotNull();
        assertThat(detail(layout, "Size:5.5\" S TYPE")).isNotNull();
        assertThat(bars(layout)).isNotNull();
    }

    @Test
    @DisplayName("where there is room, the short name heads the column in the column's own size")
    void theMarkIsKeptWhenItFits() {
        LabelSettings settings = tagSettings();
        settings.setDetailFontPt(new BigDecimal("4.00"));

        LabelLayout layout = layoutOf(gold(), settings);
        LabelLayout.Text shop = text(layout, LabelLayout.Role.SHOP);

        assertThat(shop.value()).isEqualTo("SJ");
        assertThat(shop.fontPt())
                .as("set in the column's type size, not its own")
                .isEqualByComparingTo(settings.getDetailFontPt());
        assertThat(layout.bottomMm()).isLessThanOrEqualTo(settings.getLabelHeightMm());
    }

    @Test
    @DisplayName("no side mark means the bars start at the margin, as they used to")
    void noSideMarkLeavesTheStripOut() {
        LabelSettings settings = tagSettings();
        settings.setSideMarkText("");

        LabelLayout layout = layoutOf(gold(), settings);

        assertThat(layout.elements()).noneMatch(LabelLayout.VerticalText.class::isInstance);
        // The bars sit centred in their column, so the column - not the bars -
        // is what starts at the margin. The leftmost ink on the tag is what
        // says the strip has gone.
        BigDecimal leftmost = layout.elements().stream()
                .map(element -> switch (element) {
                    case LabelLayout.Bars b -> b.xMm();
                    case LabelLayout.Text t -> t.xMm();
                    case LabelLayout.VerticalText v -> v.xMm();
                    case LabelLayout.Logo l -> l.xMm();
                })
                .reduce(BigDecimal::min)
                .orElseThrow();
        assertThat(leftmost).isEqualByComparingTo(settings.getMarginLeftMm());
    }

    @Test
    @DisplayName("the details drop below the barcode when the tag is too narrow for both")
    void stacksOnANarrowTag() {
        LabelSettings settings = tagSettings();
        settings.setLabelWidthMm(new BigDecimal("25.00"));
        settings.setContentWidthMm(new BigDecimal("25.00"));
        settings.setContentHeightMm(new BigDecimal("22.00"));
        settings.setLabelHeightMm(new BigDecimal("22.00"));

        LabelLayout layout = layoutOf(gold(), settings);

        assertThat(layout.sideBySide()).isFalse();
        assertThat(text(layout, LabelLayout.Role.NAME).yMm()).isGreaterThan(bars(layout).yMm());
    }

    @Test
    @DisplayName("shortening the printable length keeps the barcode on the head of the tag")
    void barcodeStaysOnTheHead() {
        // The shop's tag has a head and a thin neck; only the head takes print,
        // and the barcode is the part that must land on it.
        LabelSettings settings = tagSettings();
        settings.setContentWidthMm(new BigDecimal("34.00"));

        LabelLayout layout = layoutOf(gold(), settings);

        LabelLayout.Bars bars = bars(layout);
        assertThat(bars.xMm().add(bars.widthMm())).isLessThanOrEqualTo(settings.getContentWidthMm());
        LabelLayout.Text purity = text(layout, LabelLayout.Role.PURITY);
        assertThat(purity.xMm().add(LabelLayout.textWidthMm(purity.value(), purity.fontPt())))
                .isLessThanOrEqualTo(settings.getContentWidthMm());
    }

    @Test
    @DisplayName("details are never stacked off the bottom of a shallow tag")
    void neverStacksOffAShallowTag() {
        // 34 mm of head is too narrow for both blocks, but 12 mm is too shallow to
        // stack them: dropping the details below would run them off the tag.
        LabelSettings settings = tagSettings();
        settings.setContentWidthMm(new BigDecimal("34.00"));

        LabelLayout layout = layoutOf(gold(), settings);

        assertThat(layout.sideBySide()).isTrue();
        for (LabelLayout.Element element : layout.elements()) {
            if (element instanceof LabelLayout.Text line) {
                assertThat(line.yMm().add(LabelLayout.lineHeightMm(line.fontPt())))
                        .as(line.value())
                        .isLessThanOrEqualTo(settings.getLabelHeightMm());
            }
        }
    }

    @Test
    @DisplayName("a piece with a size prints it under the weight, still inside the tag")
    void sizePrintsUnderTheWeight() {
        LabelSettings settings = tagSettings();
        LabelDtos.Label ring = new LabelDtos.Label(4L, "998284", "22K / 916", "Womens Ring",
                new BigDecimal("1.560"), "11", "Gold", "Ring", "Womens Ring", "SJ", "AVAILABLE");

        LabelLayout layout = layoutOf(ring, settings);

        LabelLayout.Text weight = detail(layout, "GMS.1.560");
        LabelLayout.Text size = detail(layout, "Size:11");
        assertThat(size.yMm()).isGreaterThan(weight.yMm());
        assertThat(size.xMm()).isEqualByComparingTo(weight.xMm());
        // Three lines is the tallest the block gets; it still has to stay on the tag.
        assertThat(size.yMm().add(LabelLayout.lineHeightMm(size.fontPt())))
                .isLessThanOrEqualTo(settings.getLabelHeightMm());
        assertThat(size.xMm().add(LabelLayout.textWidthMm(size.value(), size.fontPt())))
                .isLessThanOrEqualTo(settings.getContentWidthMm());
    }

    @Test
    @DisplayName("a wordy size such as \"18 inch\" still fits")
    void aLongSizeStillFits() {
        LabelSettings settings = tagSettings();
        LabelDtos.Label chain = new LabelDtos.Label(5L, "998290", "22K / 916", "Gents Chain",
                new BigDecimal("24.750"), "18 inch", "Gold", "Chain", "Gents Chain", "SJ", "AVAILABLE");

        LabelLayout layout = layoutOf(chain, settings);

        for (LabelLayout.Element element : layout.elements()) {
            if (element instanceof LabelLayout.Text line) {
                assertThat(line.yMm().add(LabelLayout.lineHeightMm(line.fontPt())))
                        .as(line.value())
                        .isLessThanOrEqualTo(settings.getLabelHeightMm());
            }
        }
        assertThat(detail(layout, "Size:18 inch")).isNotNull();
    }

    @Test
    void weightAndSizeAreOnlyPrintedWhenThePieceHasThem() {
        LabelDtos.Label withSize = new LabelDtos.Label(2L, "998284", null, "Womens Ring",
                new BigDecimal("1.560"), "11", "Silver", "Ring", "Womens Ring", "SJ", "AVAILABLE");
        assertThat(LabelLayout.detailLines(withSize)).containsExactly("WOMENS RING", "GMS.1.560", "Size:11");

        LabelDtos.Label withoutSize = new LabelDtos.Label(3L, "905351", null, "Womens Ring",
                new BigDecimal("20.800"), "  ", "Gold", "Ring", "Womens Ring", "SJ", "AVAILABLE");
        assertThat(LabelLayout.detailLines(withoutSize)).containsExactly("WOMENS RING", "GMS.20.800");
    }

    @Test
    @DisplayName("the logo goes under the detail block, and the short name keeps its own place")
    void logoSitsUnderTheDetails() {
        // A 12 mm tag will not hold three 6 pt lines and a 6.5 mm logo, so this
        // case uses the type sizes that do fit.
        LabelSettings settings = tagSettings();
        settings.setShopMark(ShopMark.LOGO);
        settings.setShopLogoHeightMm(new BigDecimal("4.40"));
        settings.setDetailFontPt(new BigDecimal("4.00"));
        LabelDtos.Label ring = new LabelDtos.Label(4L, "998284", "22K / 916", "Womens Ring",
                new BigDecimal("1.560"), "11", "Gold", "Ring", "Womens Ring", "SJ", "AVAILABLE");

        LabelLayout layout = LabelLayout.of(ring, settings, renderer.modules("998284"), true);

        LabelLayout.Logo logo = logo(layout);
        LabelLayout.Text size = detail(layout, "Size:11");

        // Under the size line, lined up with the detail column.
        assertThat(logo.yMm()).isGreaterThan(size.yMm());
        assertThat(logo.xMm()).isEqualByComparingTo(size.xMm());
        // One shop mark, not two: where the logo is drawn, the short name is
        // not also printed above the description.
        assertThat(layout.elements())
                .as("short name as well as the logo")
                .noneMatch(element -> element instanceof LabelLayout.Text text
                        && text.role() == LabelLayout.Role.SHOP);
        // And none of it runs off the tag.
        assertThat(layout.bottomMm()).isLessThanOrEqualTo(settings.getLabelHeightMm());
    }

    @Test
    @DisplayName("a logo too big for the tag is reported by bottomMm rather than silently clipped")
    void anOversizedLogoOverflows() {
        LabelSettings settings = tagSettings();
        settings.setShopMark(ShopMark.LOGO);
        settings.setShopLogoHeightMm(new BigDecimal("6.50"));
        LabelDtos.Label ring = new LabelDtos.Label(4L, "998284", "22K / 916", "Womens Ring",
                new BigDecimal("1.560"), "11", "Gold", "Ring", "Womens Ring", "SJ", "AVAILABLE");

        LabelLayout layout = LabelLayout.of(ring, settings, renderer.modules("998284"), true);

        assertThat(layout.bottomMm()).isGreaterThan(settings.getLabelHeightMm());
    }

    @Test
    @DisplayName("with no artwork to draw, only the logo is dropped - the short name is unaffected")
    void fallsBackToTheShortNameWithoutArtwork() {
        LabelSettings settings = tagSettings();
        settings.setShopMark(ShopMark.LOGO);
        settings.setShopLogoHeightMm(new BigDecimal("4.40"));

        LabelLayout layout = LabelLayout.of(gold(), settings, renderer.modules("905351"), false);

        assertThat(layout.elements()).noneMatch(LabelLayout.Logo.class::isInstance);
        assertThat(text(layout, LabelLayout.Role.SHOP).value()).isEqualTo("SJ");
    }

    @Test
    @DisplayName("NONE prints neither the logo nor the short name")
    void noMarkLeavesTheBarcodeAtTheMargin() {
        LabelSettings settings = tagSettings();
        settings.setShopMark(ShopMark.NONE);
        settings.setShopLogoHeightMm(new BigDecimal("4.40"));

        LabelLayout layout = LabelLayout.of(gold(), settings, renderer.modules("905351"), true);

        assertThat(layout.elements()).noneMatch(LabelLayout.Logo.class::isInstance);
        assertThat(layout.elements()).noneMatch(
                e -> e instanceof LabelLayout.Text t && t.role() == LabelLayout.Role.SHOP);
        assertThat(bars(layout).xMm()).isGreaterThanOrEqualTo(settings.getMarginLeftMm());
    }

    private static LabelLayout.Logo logo(LabelLayout layout) {
        return layout.elements().stream()
                .filter(LabelLayout.Logo.class::isInstance)
                .map(LabelLayout.Logo.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no logo on the tag"));
    }

    /** The shop's own two tags, from the pair they photographed. */
    private static LabelDtos.Label salangai(String serial) {
        return new LabelDtos.Label(1L, serial, null, "7\" Salangai", new BigDecimal("77.180"), "7 inch",
                "Silver", "Anklet", "Salangai", "SJ", "AVAILABLE");
    }

    @Test
    @DisplayName("the description starts in the same place whatever the barcode comes to")
    void descriptionDoesNotFollowTheBars() {
        LabelSettings settings = tagSettings();
        settings.setShowPurity(false);

        // 151 is three digits and takes 68 modules - an odd digit count forces
        // a code switch - where the four digits of 3130 take 57. Left to
        // follow the bars, the two descriptions would sit 2.75 mm apart.
        LabelLayout.Bars shortSerial = bars(layoutOf(salangai("151"), settings));
        LabelLayout.Bars longSerial = bars(layoutOf(salangai("3130"), settings));
        assertThat(shortSerial.widthMm()).isGreaterThan(longSerial.widthMm());

        assertThat(text(layoutOf(salangai("3130"), settings), LabelLayout.Role.NAME).xMm())
                .isEqualByComparingTo(text(layoutOf(salangai("151"), settings), LabelLayout.Role.NAME).xMm());
    }

    @Test
    @DisplayName("and never before the middle of the tag, however short the barcode")
    void descriptionStartsAtTheMiddleOfTheTag() {
        LabelSettings settings = tagSettings();
        settings.setShowPurity(false);
        BigDecimal middle = settings.getLabelWidthMm().divide(new BigDecimal("2"), 3, java.math.RoundingMode.HALF_UP);

        for (String serial : new String[] {"1", "151", "3130", "905351"}) {
            LabelLayout layout = layoutOf(salangai(serial), settings);
            assertThat(text(layout, LabelLayout.Role.NAME).xMm())
                    .as("description on tag %s", serial)
                    .isGreaterThanOrEqualTo(middle);
        }
    }

    @Test
    @DisplayName("but a description too wide for the right half follows the bars instead")
    void aLongDescriptionKeepsItsRoom() {
        LabelSettings settings = tagSettings();
        settings.setShowPurity(false);
        settings.setMarginLeftMm(new BigDecimal("-20.00"));
        BigDecimal tagLeft = settings.getMarginLeftMm();
        BigDecimal middle = tagLeft.add(new BigDecimal("30.000"));
        BigDecimal tagRight = tagLeft.add(settings.getLabelWidthMm());

        // 25 characters at 6 pt come to 30.7 mm: too wide for the 30 mm right
        // half, and comfortable in the 33.2 mm the bars and side mark leave.
        LabelDtos.Label wordy = new LabelDtos.Label(1L, "151",
                null, "Ladies Antique Temple Set", new BigDecimal("77.180"), null,
                "Silver", "Anklet", "Salangai", "SJ", "AVAILABLE");
        LabelLayout.Text name = text(layoutOf(wordy, settings), LabelLayout.Role.NAME);

        // It gives up the middle rather than the last few letters.
        assertThat(name.xMm()).isLessThan(middle);
        assertThat(name.xMm().add(LabelLayout.textWidthMm(name.value(), name.fontPt())))
                .isLessThanOrEqualTo(tagRight);
    }

    @Test
    @DisplayName("the middle is the tag's, not the origin's, when the margin is negative")
    void theMiddleIsMeasuredFromTheTagsOwnEdge() {
        LabelSettings settings = tagSettings();
        settings.setShowPurity(false);
        // How the shop undoes a margin its printer adds: the artwork starts
        // 20 mm before the tag, so the origin is off the front of it.
        settings.setMarginLeftMm(new BigDecimal("-20.00"));

        LabelLayout layout = layoutOf(salangai("151"), settings);
        // 20 mm before the tag plus half of 60 - the tag's own middle, not the
        // 30 mm that measuring from the origin would give.
        assertThat(text(layout, LabelLayout.Role.NAME).xMm()).isEqualByComparingTo("10.000");
        assertThat(layout.sideBySide()).isTrue();
    }

    private static LabelLayout.Text detail(LabelLayout layout, String value) {
        return layout.elements().stream()
                .filter(LabelLayout.Text.class::isInstance)
                .map(LabelLayout.Text.class::cast)
                .filter(text -> text.value().equals(value))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no label line reading \"" + value + "\""));
    }

    private static LabelLayout.Bars bars(LabelLayout layout) {
        return layout.elements().stream()
                .filter(LabelLayout.Bars.class::isInstance)
                .map(LabelLayout.Bars.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static LabelLayout.Text text(LabelLayout layout, LabelLayout.Role role) {
        return layout.elements().stream()
                .filter(LabelLayout.Text.class::isInstance)
                .map(LabelLayout.Text.class::cast)
                .filter(text -> text.role() == role)
                .findFirst()
                .orElseThrow();
    }
}
