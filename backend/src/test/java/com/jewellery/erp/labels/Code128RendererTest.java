package com.jewellery.erp.labels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.oned.Code128Reader;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.labels.service.Code128Renderer;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The barcode is the whole point of a label: if it does not scan back as the
 * serial number, the tag is worse than useless. So these tests do not inspect
 * the SVG - they decode the bars with a barcode reader, the way the shop's
 * scanner will.
 */
class Code128RendererTest {

    private static final BigDecimal MODULE_MM = new BigDecimal("0.250");
    private static final BigDecimal HEIGHT_MM = new BigDecimal("10.00");
    private static final Pattern RECT =
            Pattern.compile("<rect x=\"([0-9.]+)\" y=\"0\" width=\"([0-9.]+)\"");

    private final Code128Renderer renderer = new Code128Renderer();

    @ParameterizedTest
    @ValueSource(strings = {"000123", "905351", "GOLD2026000123", "ABC123456", "1", "000000000000000000001"})
    @DisplayName("a rendered barcode scans back as exactly the value it was given")
    void scansBackAsTheSerialNumber(String serial) throws Exception {
        Code128Renderer.Barcode barcode = renderer.render(serial, MODULE_MM, HEIGHT_MM);

        assertThat(decode(barcode.svg())).isEqualTo(serial);
        assertThat(barcode.value()).isEqualTo(serial);
    }

    @Test
    @DisplayName("nothing but the serial number is encoded - no purity, shop name or weight rides along")
    void encodesOnlyTheSerialNumber() throws Exception {
        String serial = "000123";
        Code128Renderer.Barcode barcode = renderer.render(serial, MODULE_MM, HEIGHT_MM);

        String decoded = decode(barcode.svg());
        assertThat(decoded).isEqualTo(serial);
        assertThat(decoded).doesNotContain("916", "22K", "SJ", "Gold");
    }

    @Test
    void widthFollowsTheConfiguredNarrowBar() {
        Code128Renderer.Barcode thin = renderer.render("000123", new BigDecimal("0.250"), HEIGHT_MM);
        Code128Renderer.Barcode thick = renderer.render("000123", new BigDecimal("0.500"), HEIGHT_MM);

        assertThat(thick.widthMm()).isEqualByComparingTo(thin.widthMm().multiply(BigDecimal.valueOf(2)));
        assertThat(thin.svg()).contains("height=\"10mm\"");
    }

    @Test
    void refusesAnEmptyValue() {
        assertThatThrownBy(() -> renderer.render("  ", MODULE_MM, HEIGHT_MM))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("serial number");
    }

    /** Rebuilds the bar pattern from the SVG and reads it with ZXing's Code 128 reader. */
    private static String decode(String svg) throws Exception {
        Matcher matcher = RECT.matcher(svg);
        double moduleMm = MODULE_MM.doubleValue();
        java.util.List<int[]> bars = new java.util.ArrayList<>();
        int modules = 0;
        while (matcher.find()) {
            int start = (int) Math.round(Double.parseDouble(matcher.group(1)) / moduleMm);
            int width = (int) Math.round(Double.parseDouble(matcher.group(2)) / moduleMm);
            bars.add(new int[] {start, width});
            modules = Math.max(modules, start + width);
        }
        // A quiet zone on both sides, as a printed label has.
        int quiet = 10;
        BitMatrix matrix = new BitMatrix(modules + 2 * quiet, 1);
        for (int[] bar : bars) {
            for (int x = 0; x < bar[1]; x++) {
                matrix.set(quiet + bar[0] + x, 0);
            }
        }
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        Result result = new Code128Reader().decode(
                new BinaryBitmap(new GlobalHistogramBinarizer(new BitMatrixSource(matrix))), hints);
        return result.getText();
    }

    /** Minimal LuminanceSource over a one-row BitMatrix. */
    private static final class BitMatrixSource extends com.google.zxing.LuminanceSource {
        private final BitMatrix matrix;

        BitMatrixSource(BitMatrix matrix) {
            super(matrix.getWidth(), matrix.getHeight());
            this.matrix = matrix;
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            byte[] result = row != null && row.length >= getWidth() ? row : new byte[getWidth()];
            for (int x = 0; x < getWidth(); x++) {
                result[x] = (byte) (matrix.get(x, y) ? 0 : (byte) 0xFF);
            }
            return result;
        }

        @Override
        public byte[] getMatrix() {
            byte[] result = new byte[getWidth() * getHeight()];
            for (int y = 0; y < getHeight(); y++) {
                getRow(y, new byte[getWidth()]);
                for (int x = 0; x < getWidth(); x++) {
                    result[y * getWidth() + x] = (byte) (matrix.get(x, y) ? 0 : (byte) 0xFF);
                }
            }
            return result;
        }
    }
}
