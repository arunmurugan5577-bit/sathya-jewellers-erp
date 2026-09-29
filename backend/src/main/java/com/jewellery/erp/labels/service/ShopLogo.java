package com.jewellery.erp.labels.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The shop logo, prepared for a thermal head.
 *
 * <p>A label printer burns dots: there is no grey and no colour, so the logo has
 * to become black and white before it is drawn or the driver will dither it into
 * a smudge. Scaling to the exact dot count first and thresholding second is what
 * keeps the monogram readable - at 203 dpi a 6.5 mm box is only 52 dots across.
 *
 * <p>Results are cached per pixel size. The artwork never changes at runtime and
 * rebuilding it for every label in a batch would be wasted work.
 */
@Component
public class ShopLogo {

    private static final Logger log = LoggerFactory.getLogger(ShopLogo.class);
    private static final String RESOURCE = "/branding/label-logo.png";

    /**
     * Luminance below which a pixel is burnt.
     *
     * <p>Chosen by rendering the logo at 32 to 64 dots and looking at it: lower
     * and the dotted ring closes up into the disc, higher and the ring is lost
     * altogether. At this value the ring survives and the letters stay open.
     */
    private static final int BURN_BELOW = 150;

    /** Assumed head resolution when working out how many dots a box is worth. */
    private static final double DOTS_PER_MM = 203d / 25.4d;

    private final Map<Integer, BufferedImage> bySize = new ConcurrentHashMap<>();
    private final Map<Integer, String> dataUris = new ConcurrentHashMap<>();
    private volatile BufferedImage source;
    private volatile boolean sourceMissing;

    /** True when there is artwork to draw; false means fall back to the short name. */
    public boolean isAvailable() {
        return loadSource() != null;
    }

    /**
     * The logo reduced to pure black and white at the size it will be printed.
     *
     * @param heightMm the square box the logo is drawn in
     * @return empty when the artwork could not be read
     */
    public Optional<BufferedImage> forHeight(java.math.BigDecimal heightMm) {
        BufferedImage original = loadSource();
        if (original == null) {
            return Optional.empty();
        }
        int dots = dotsFor(heightMm);
        return Optional.of(bySize.computeIfAbsent(dots, size -> monochrome(original, size)));
    }

    /** The same bitmap as a {@code data:} URI, for the on-screen preview. */
    public Optional<String> dataUri(java.math.BigDecimal heightMm) {
        int dots = dotsFor(heightMm);
        String cached = dataUris.get(dots);
        if (cached != null) {
            return Optional.of(cached);
        }
        return forHeight(heightMm).map(image -> {
            String uri = encode(image);
            dataUris.put(dots, uri);
            return uri;
        });
    }

    private static int dotsFor(java.math.BigDecimal heightMm) {
        // At least a few dots, so a silly setting cannot ask for a zero-pixel image.
        return Math.max(8, (int) Math.round(heightMm.doubleValue() * DOTS_PER_MM));
    }

    private BufferedImage loadSource() {
        if (sourceMissing) {
            return null;
        }
        BufferedImage current = source;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (source == null && !sourceMissing) {
                try (InputStream in = ShopLogo.class.getResourceAsStream(RESOURCE)) {
                    source = in == null ? null : ImageIO.read(in);
                } catch (IOException ex) {
                    log.warn("Could not read the label logo from {}", RESOURCE, ex);
                    source = null;
                }
                if (source == null) {
                    sourceMissing = true;
                    log.warn("No label logo at {}; tags will fall back to the shop short name", RESOURCE);
                }
            }
            return source;
        }
    }

    private static BufferedImage monochrome(BufferedImage original, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        // White, so anything transparent in the artwork reads as unprinted tag.
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(original, 0, 0, size, size, null);
        g.dispose();

        BufferedImage flat = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = scaled.getRGB(x, y);
                int luminance = (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
                flat.setRGB(x, y, luminance < BURN_BELOW ? 0x000000 : 0xFFFFFF);
            }
        }
        return flat;
    }

    private static String encode(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException ex) {
            log.warn("Could not encode the label logo for the preview", ex);
            return "";
        }
    }
}
