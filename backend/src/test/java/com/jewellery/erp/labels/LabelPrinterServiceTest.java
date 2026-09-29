package com.jewellery.erp.labels;

import static org.assertj.core.api.Assertions.assertThat;

import com.jewellery.erp.labels.dto.LabelDtos;
import com.jewellery.erp.labels.entity.LabelSettings;
import com.jewellery.erp.labels.entity.ShopMark;
import com.jewellery.erp.labels.service.Code128Renderer;
import com.jewellery.erp.labels.service.LabelPrinterService;
import com.jewellery.erp.labels.service.ShopLogo;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the printer is actually handed.
 *
 * <p>Rendered into an image with the page clip the printing system applies, so a
 * design that runs off the page shows up here rather than on a wasted tag.
 */
class LabelPrinterServiceTest {

    private static final double POINTS_PER_MM = 72d / 25.4d;
    private static final double SCALE = 8;

    private final LabelPrinterService printer =
            new LabelPrinterService(new Code128Renderer(), new ShopLogo());

    private static LabelSettings tagSettings() {
        LabelSettings settings = new LabelSettings();
        settings.setShopShortName("SJ");
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
        settings.setShopMark(ShopMark.TEXT);
        settings.setShopLogoHeightMm(new BigDecimal("6.50"));
        settings.setDetailFontPt(new BigDecimal("6.00"));
        settings.setShowPurity(true);
        return settings;
    }

    private static LabelDtos.Label gold() {
        return new LabelDtos.Label(1L, "905351", "22K / 916", "Womens Ring", new BigDecimal("5.250"), null,
                "Gold", "Ring", "Womens Ring", "SJ", "AVAILABLE");
    }

    /** Ink extent in mm from the left edge of the page, or null when nothing was drawn. */
    private double[] inkSpanMm(LabelSettings settings, LabelDtos.Label label) {
        int width = (int) Math.round(
                LabelPrinterService.pageWidthMm(settings).doubleValue() * POINTS_PER_MM * SCALE);
        int height = (int) Math.round(
                settings.getLabelHeightMm().doubleValue() * POINTS_PER_MM * SCALE);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        // Exactly what PrinterJob hands a Printable: a clip of the imageable area,
        // which is the whole page.
        g.setClip(0, 0, width, height);
        g.translate(LabelPrinterService.leadMm(settings).doubleValue() * POINTS_PER_MM * SCALE, 0);
        g.scale(SCALE, SCALE);
        printer.renderLabel(g, label, settings);
        g.dispose();

        int left = -1;
        int right = -1;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if ((image.getRGB(x, y) & 0xFF) < 128) {
                    if (left < 0) {
                        left = x;
                    }
                    right = x;
                    break;
                }
            }
        }
        return left < 0 ? null : new double[] {left / (POINTS_PER_MM * SCALE), right / (POINTS_PER_MM * SCALE)};
    }

    @Test
    @DisplayName("the design starts at the left margin and stays on the tag")
    void drawsAtTheLeftMargin() {
        LabelSettings settings = tagSettings();

        double[] ink = inkSpanMm(settings, gold());

        assertThat(ink).isNotNull();
        assertThat(ink[0]).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.5));
        assertThat(ink[1]).isLessThan(settings.getLabelWidthMm().doubleValue());
    }

    @Test
    @DisplayName("a negative left margin moves the whole design without clipping it")
    void negativeLeftMarginIsNotClipped() {
        // The driver lays the page down some way into the tag; a negative margin is
        // what drags the print back. It only works if the page grows to the left
        // with it - otherwise the barcode silently loses its first bars.
        LabelSettings settings = tagSettings();
        double[] before = inkSpanMm(settings, gold());

        settings.setMarginLeftMm(new BigDecimal("-12.80"));
        double[] after = inkSpanMm(settings, gold());

        assertThat(after).isNotNull();
        // Nothing lost: the design is the same length as before, only moved.
        assertThat(after[1] - after[0]).isCloseTo(before[1] - before[0], org.assertj.core.data.Offset.offset(0.2));
        assertThat(LabelPrinterService.leadMm(settings)).isEqualByComparingTo("12.80");
        assertThat(LabelPrinterService.pageWidthMm(settings)).isEqualByComparingTo("72.80");
        // It begins right at the start of the widened page, not chopped off at it.
        assertThat(after[0]).isGreaterThan(0);
    }

    @Test
    @DisplayName("a positive left margin leaves the page the size of the tag")
    void positiveMarginDoesNotGrowThePage() {
        LabelSettings settings = tagSettings();

        assertThat(LabelPrinterService.leadMm(settings)).isEqualByComparingTo("0");
        assertThat(LabelPrinterService.pageWidthMm(settings)).isEqualByComparingTo("60.00");
    }
}
