package com.jewellery.erp.labels.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Printer calibration for barcode labels - one row, like shop settings.
 *
 * <p>Every millimetre of the label lives here rather than in the layout code,
 * because getting a thermal label to line up with a physical jewellery tag is a
 * matter of trying a value, printing, and adjusting. That is a settings screen,
 * not a code change.
 */
@Entity
@Table(name = "label_settings")
@Getter
@Setter
@NoArgsConstructor
public class LabelSettings extends AuditableEntity {

    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    private Long id = SINGLETON_ID;

    /** Printed beside the barcode, e.g. "SJ" for Sathya Jewellers. */
    @Column(name = "shop_short_name", nullable = false, length = 8)
    private String shopShortName;

    @Column(name = "label_width_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal labelWidthMm;

    @Column(name = "label_height_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal labelHeightMm;

    /** Tags side by side across the roll. One printed page is one row of these. */
    @Column(name = "labels_across", nullable = false)
    private short labelsAcross = 1;

    /** Printable length of the tag from its left edge; the neck and tail stay blank. */
    @Column(name = "content_width_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal contentWidthMm;

    /** Printable height at the top of each tag; the tail below stays blank. */
    @Column(name = "content_height_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal contentHeightMm;

    @Column(name = "margin_top_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginTopMm;

    @Column(name = "margin_left_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginLeftMm;

    /** Shifts the whole printed image, for when it sits off the physical tag. */
    @Column(name = "offset_x_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal offsetXMm;

    @Column(name = "offset_y_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal offsetYMm;

    @Column(name = "rotation_degrees", nullable = false)
    private short rotationDegrees;

    @Column(name = "barcode_height_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal barcodeHeightMm;

    /** Width of the narrowest bar; 0.25 mm is one dot at 203 dpi. */
    @Column(name = "barcode_module_mm", nullable = false, precision = 6, scale = 3)
    private BigDecimal barcodeModuleMm;

    @Column(name = "serial_font_pt", nullable = false, precision = 5, scale = 2)
    private BigDecimal serialFontPt;

    @Column(name = "purity_font_pt", nullable = false, precision = 5, scale = 2)
    private BigDecimal purityFontPt;

    @Column(name = "shop_font_pt", nullable = false, precision = 5, scale = 2)
    private BigDecimal shopFontPt;

    /** Whether the tag carries the shop logo, its short name, or neither. */
    @Enumerated(EnumType.STRING)
    @Column(name = "shop_mark", nullable = false, length = 10)
    private ShopMark shopMark = ShopMark.LOGO;

    /** The square box the logo is drawn in. Under about 4 mm it stops reading. */
    @Column(name = "shop_logo_height_mm", nullable = false, precision = 5, scale = 2)
    private BigDecimal shopLogoHeightMm;

    /** Type size for the item name, weight and size beside the barcode. */
    @Column(name = "detail_font_pt", nullable = false, precision = 5, scale = 2)
    private BigDecimal detailFontPt;

    /** Windows printer the labels go to; empty means the machine default. */
    @Column(name = "printer_name", length = 160)
    private String printerName;

    /** Whether this machine spools the labels, or the shop's agent does. */
    @Enumerated(EnumType.STRING)
    @Column(name = "print_mode", nullable = false, length = 10)
    private PrintMode printMode = PrintMode.DIRECT;

    @Column(name = "show_purity", nullable = false)
    private boolean showPurity = true;
}
