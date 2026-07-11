package com.fih.companion.badge;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service
public class BadgePdfService {

    // ---- palette ------------------------------------------------------------
    // BLUE / NAVY / GREY are the historical constants and keep their meaning.
    // GOLD is the one addition: the Carthage posters are navy + warm gold, and a
    // premium A4 page needs a second accent to carry rules and eyebrow labels.
    private static final Color BLUE = new Color(0x19, 0x3C, 0x8D);   // accent bars
    private static final Color NAVY = new Color(0x16, 0x2E, 0x6E);   // headings / values
    private static final Color GREY = new Color(0x6E, 0x78, 0x87);   // labels / secondary
    private static final Color GOLD = new Color(0xC0, 0x99, 0x4B);   // hairlines / eyebrows
    private static final Color HAIRLINE = new Color(0xDD, 0xE1, 0xE7);
    private static final Color CARD_BG = new Color(0xF6, 0xF7, 0xF9);
    private static final Color WHITE = Color.WHITE;

    private static final float MM = 72f / 25.4f;        // millimetres -> points

    /** "samedi 4 juillet 2026" — capitalised to "Samedi 4 juillet 2026" at draw time. */
    private static final DateTimeFormatter FULL_DATE_FR =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);

    // ---- A4 page geometry, in millimetres (origin: top-left) ----------------
    private static final float T_W = 210f, T_H = 297f;   // logical page used for scaling
    private static final float M_L = 15f, M_R = 195f;    // side margins
    private static final float CONTENT_W = M_R - M_L;    // 180 mm

    private static final float TOP_BAND_H = 8f;          // navy header band
    private static final float TOP_RULE_H = 1.4f;        // gold hairline under it

    private static final float POSTER_T = 18f, POSTER_B = 118f;   // poster band

    private static final float TITLE_Y = 130f;           // event title baseline zone
    private static final float TITLE_RULE_Y = 139f;      // short gold rule under the title

    private static final float HERO_T = 147f, HERO_B = 175f;      // holder-name card
    private static final float HERO_NAME_Y = 165.5f;              // name baseline (optically centred)
    private static final float GRID_T = 181f, GRID_B = 217f;      // 2x2 details card

    private static final float SERIAL_Y = 222f;          // serial line, right-aligned
    private static final float SERIAL_RULE_Y = 225.4f;   // hairline separating it from the codes

    private static final float QR_L = 20f, QR_SIZE = 36f, QR_T = 228f;   // QR block
    private static final float BC_L = 68f, BC_R = 192f;                  // barcode block
    private static final float BC_T = 234f, BC_H = 22f;
    private static final float BC_TEXT_Y = 262f;

    private static final float NOTE_Y = 276f;            // e-ticket instruction line
    private static final float BOT_RULE_Y = 287.6f;      // gold hairline above the footer band

    private static final float CARD_RADIUS = 2.6f;       // card corner radius (mm)

    private final BadgeProperties props;

    private BaseFont bfBold;
    private BaseFont bfReg;
    private BaseFont bfObl;

    public BadgePdfService(BadgeProperties props) {
        this.props = props;
        try {
            bfBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            bfReg = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            bfObl = BaseFont.createFont(BaseFont.HELVETICA_OBLIQUE, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the base PDF fonts", e);
        }
    }

    private Rectangle ticketPage() {
        return new Rectangle((float) (props.getTicketWidthMm() * MM), (float) (props.getTicketHeightMm() * MM));
    }

    // ----------------------------------------------------------- public API
    /** One invitation, sized to the configured page (A4 portrait by default). */
    public byte[] single(BadgeRecord rec) {
        return single(rec, new HashMap<>());
    }

    /**
     * One invitation, reusing a per-batch poster cache so the poster file is read,
     * downscaled and re-encoded ONCE for the whole batch instead of once per
     * badge (the old behaviour re-read and re-embedded the full-size poster
     * into every PDF, which is what made « Tout générer » blow up on ~200 badges).
     */
    private byte[] single(BadgeRecord rec, Map<String, byte[]> posterCache) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Document doc = new Document(ticketPage(), 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(doc, os);
        // Smallest possible file: PDF 1.5 object/xref streams + max deflate on the
        // content streams. The QR and the barcode are 1-bit rasters and the poster
        // is an already-compressed JPEG, so this is lossless — nothing that the
        // scanner reads is degraded.
        writer.setFullCompression();
        writer.setCompressionLevel(9);
        doc.open();
        Rectangle p = doc.getPageSize();
        drawTicket(writer.getDirectContent(), writer, rec, posterCache, 0, 0, p.getWidth(), p.getHeight());
        doc.close();
        return os.toByteArray();
    }


    /**
     * Builds the whole badge ZIP into a byte[] — one PDF is generated at a
     * time (peak memory ≈ the finished ZIP + one PDF), so with the poster-size
     * fix a 200-badge archive is only ~10 MB. Returning a concrete byte[] lets
     * the controller send a real Content-Length, which is what makes the
     * browser reliably show the download (a chunked/streaming response gave no
     * length and the large blob wouldn't fire the save on the client).
     *
     * Entries are STOREd (NO_COMPRESSION): each PDF is dominated by an
     * already-compressed JPEG, so deflating again is CPU for ~0 gain.
     */
    public byte[] batchZipPerAffectee(List<BadgeRecord> recs) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Map<String, Integer> used = new HashMap<>();
        Map<String, byte[]> posterCache = new HashMap<>();
        try (ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.setLevel(Deflater.NO_COMPRESSION);
            for (BadgeRecord rec : recs) {
                String entry = uniqueEntryName(fileBaseName(rec), rec, used);
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(single(rec, posterCache));
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build badge ZIP", e);
        }
        return os.toByteArray();
    }

    /**
     * File name for ONE invitation PDF, identical to the entry name used inside
     * the batch ZIP: « Affecté à » first, then the holder, then SANS-NOM_<code>.
     * BadgeController uses it for the single-download Content-Disposition, so a
     * single PDF and its ZIP counterpart are named the same way.
     */
    public String fileName(BadgeRecord rec) {
        return fileBaseName(rec) + ".pdf";
    }

    /** Base file name (no extension) for one invitation: affecteeA -> holder -> SANS-NOM_code. */
    private String fileBaseName(BadgeRecord rec) {
        if (rec.affecteeA() != null && !rec.affecteeA().isBlank()) {
            return sanitizeFilename(rec.affecteeA());
        }
        if (rec.holderName() != null && !rec.holderName().isBlank()) {
            return sanitizeFilename(rec.holderName());
        }
        String code = (rec.codebarre() != null && !rec.codebarre().isBlank())
                ? rec.codebarre() : rec.numeroserie();
        return "SANS-NOM_" + sanitizeFilename(code);
    }

    /** Ensure ZIP entry names are unique (case-insensitively) within one archive. */
    private String uniqueEntryName(String base, BadgeRecord rec, Map<String, Integer> used) {
        String candidate = base + ".pdf";
        if (used.putIfAbsent(candidate.toLowerCase(Locale.ROOT), 1) == null) {
            return candidate;
        }
        String withSerial = base + "_" + sanitizeFilename(rec.numeroserie()) + ".pdf";
        if (used.putIfAbsent(withSerial.toLowerCase(Locale.ROOT), 1) == null) {
            return withSerial;
        }
        int n = used.merge(withSerial.toLowerCase(Locale.ROOT), 1, Integer::sum);
        return base + "_" + sanitizeFilename(rec.numeroserie()) + "_" + n + ".pdf";
    }


    private String sanitizeFilename(String s) {
        if (s == null) return "badge";
        String folded = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")                 // drop diacritics
                .replace(' ', '_')
                .replaceAll("[^a-zA-Z0-9._-]+", "-")       // unsafe -> dash
                .replaceAll("[-_]{2,}", "_")               // collapse repeats
                .replaceAll("(^[._-]+|[._-]+$)", "");      // trim separators
        if (folded.isBlank()) return "badge";
        return folded.length() > 120 ? folded.substring(0, 120) : folded;
    }

    public int zipThreshold() {
        return props.getZipThreshold();
    }

    // ------------------------------------------------------ one page drawing

    /**
     * A4 portrait invitation. The vertical rhythm is fixed in top-anchored
     * millimetres so the page is identical for every badge; only the poster,
     * the strings and the two codes vary.
     *
     *   0 …  8    navy header band + gold hairline
     *  18 … 118   poster band (contain-fit, aspect preserved, centred)
     * 128 … 139   event title + gold rule
     * 147 … 175   holder-name card (gold accent bar)
     * 181 … 217   2×2 details card (type / date / heure / lieu)
     * 222 … 225   N° de série (right-aligned) + hairline
     * 228 … 264   QR (left) + Code128 (right) + human-readable code
     * 276         instruction line
     * 288 … 297   gold hairline + navy footer band
     */
    private void drawTicket(PdfContentByte cb, PdfWriter writer, BadgeRecord rec,
                            Map<String, byte[]> posterCache,
                            float x0, float y0, float w, float h) {
        final float s = w / (T_W * MM);   // points-per-(logical mm*MM); 1.0 at A4

        // ---- 1) header band + gold hairline (full bleed)
        fillBand(cb, x0, y0, w, h, s, 0f, TOP_BAND_H, NAVY);
        fillBand(cb, x0, y0, w, h, s, TOP_BAND_H, TOP_BAND_H + TOP_RULE_H, GOLD);

        // ---- 2) poster, contain-fit inside its band (never cropped)
        drawPoster(cb, rec, posterCache, x0, y0, h, s);

        // ---- 3) event title, centred, fitted to the content width
        String title = safe(rec.eventTitle(), "FIH 2026");
        float titleSize = fitFont(bfBold, title.toUpperCase(Locale.FRENCH), 21f * s, 11f * s, CONTENT_W * MM * s);
        text(cb, bfBold, titleSize, NAVY, Element.ALIGN_CENTER,
                title.toUpperCase(Locale.FRENCH), X(x0, (M_L + M_R) / 2f, s), Yt(y0, h, TITLE_Y, s));

        // short gold rule: the only ornament, it anchors the title to the block below
        rule(cb, x0, y0, h, s, (M_L + M_R) / 2f - 16f, (M_L + M_R) / 2f + 16f, TITLE_RULE_Y, GOLD, 1.1f);

        // ---- 4) holder card: gold accent bar (echoes the rule above), name only.
        // The « AFFECTÉ À » label is intentionally gone — the name is the only
        // thing in the card, so the label was redundant furniture.
        card(cb, x0, y0, h, s, M_L, HERO_T, M_R, HERO_B, WHITE, HAIRLINE);
        cb.setColorFill(GOLD);
        cb.rectangle(X(x0, M_L, s), Yt(y0, h, HERO_B, s),
                2.6f * MM * s, (HERO_B - HERO_T) * MM * s);
        cb.fill();

        String name = displayName(rec);
        String nameOut = name == null ? "\u2014" : name;
        float nameSize = fitFont(bfBold, nameOut, 22f * s, 10f * s, (M_R - M_L - 22f) * MM * s);
        text(cb, bfBold, nameSize, NAVY, Element.ALIGN_LEFT, nameOut,
                X(x0, M_L + 10f, s), Yt(y0, h, HERO_NAME_Y, s));

        // ---- 5) details card: type / date | heure / lieu
        card(cb, x0, y0, h, s, M_L, GRID_T, M_R, GRID_B, CARD_BG, HAIRLINE);
        float colL = M_L + 9f;
        float colR = M_L + 9f + (CONTENT_W - 18f) / 2f;
        float colW = (CONTENT_W - 24f) / 2f;

        String type = safe(rec.modelName(), "Invitation");
        String dateStr = rec.eventDate() == null ? "\u2014"
                : capitalizeFirst(rec.eventDate().format(FULL_DATE_FR));
        String time = props.resolveShowTime(rec.eventDate());
        String lieu = safe(props.getVenueName(), "Th\u00e9\u00e2tre Antique de Carthage");

        pair(cb, x0, y0, h, s, "TYPE D'INVITATION", type, colL, GRID_T + 9f, colW);
        pair(cb, x0, y0, h, s, "DATE", dateStr, colR, GRID_T + 9f, colW);
        pair(cb, x0, y0, h, s, "HEURE", time, colL, GRID_T + 24f, colW);
        pair(cb, x0, y0, h, s, "LIEU", lieu, colR, GRID_T + 24f, colW);

        // ---- 5b) N° de série. Deliberately quiet: it is administrative metadata,
        // not something the guest reads, so it sits right-aligned on a hairline
        // above the code block rather than competing with the name or the date.
        String serial = safe(rec.numeroserie(), "");
        if (!serial.isBlank()) {
            float vSize = 9.5f * s;
            float vWidth = bfBold.getWidthPoint(serial, vSize);
            float right = X(x0, M_R, s);
            float base = Yt(y0, h, SERIAL_Y, s);
            text(cb, bfBold, vSize, NAVY, Element.ALIGN_RIGHT, serial, right, base);
            cb.beginText();
            cb.setFontAndSize(bfBold, 7f * s);
            cb.setColorFill(GREY);
            cb.setCharacterSpacing(1.1f * s);
            cb.showTextAligned(Element.ALIGN_RIGHT, "N\u00b0 DE S\u00c9RIE",
                    right - vWidth - 3.2f * MM * s, base, 0);
            cb.setCharacterSpacing(0f);
            cb.endText();
        }
        rule(cb, x0, y0, h, s, M_L, M_R, SERIAL_RULE_Y, HAIRLINE, 0.6f);

        // ---- 6) QR (left) — unchanged encoding: the codebarre
        try {
            // forceBW so OpenPDF stores the QR as a 1-bit bilevel image (a QR is
            // pure black/white) instead of a 24-bit RGB raster — a few KB saved
            // per badge with zero visible change.
            Image qr = Image.getInstance(qrImage(rec.codebarre(), 256), null, true);
            float qs = QR_SIZE * MM * s;
            qr.scaleAbsolute(qs, qs);
            qr.setAbsolutePosition(X(x0, QR_L, s), Yt(y0, h, QR_T + QR_SIZE, s));
            cb.addImage(qr);
        } catch (Exception ignore) {
            // QR carries the code; if it fails the page still prints the barcode
        }

        // ---- 7) Code128 barcode (right) + human-readable code beneath it
        String code = barcodePayload(rec);
        if (code != null) {
            try {
                float bw = (BC_R - BC_L) * MM * s;
                float bh = BC_H * MM * s;
                Image bar = Image.getInstance(barcodeImage(code, 700, 140), null, true);
                bar.scaleAbsolute(bw, bh);
                bar.setAbsolutePosition(X(x0, BC_L, s), Yt(y0, h, BC_T + BC_H, s));
                cb.addImage(bar);
            } catch (Exception ignore) {
                // unencodable payload (non-ASCII): the QR alone still carries it
            }
            text(cb, bfReg, 8.5f * s, GREY, Element.ALIGN_CENTER, spaced(code),
                    X(x0, (BC_L + BC_R) / 2f, s), Yt(y0, h, BC_TEXT_Y, s));
        }

        // ---- 8) instruction line + footer band
        text(cb, bfObl, 7.5f * s, GREY, Element.ALIGN_CENTER,
                "Ceci est votre invitation \u00e0 pr\u00e9senter au contr\u00f4le d'acc\u00e8s",
                X(x0, (M_L + M_R) / 2f, s), Yt(y0, h, NOTE_Y, s));
        fillBand(cb, x0, y0, w, h, s, BOT_RULE_Y, BOT_RULE_Y + TOP_RULE_H, GOLD);
        fillBand(cb, x0, y0, w, h, s, BOT_RULE_Y + TOP_RULE_H, T_H, NAVY);
    }

    // --------------------------------------------------------------- helpers

    /**
     * Poster band. Resolution and embedding are UNCHANGED (resolvePoster +
     * scaledPosterBytes + per-batch cache); only the destination rectangle
     * differs: on A4 the poster is contain-fitted (whole image visible, aspect
     * preserved, centred) instead of cover-cropped, because the FIH posters are
     * wide banners and cropping them to a portrait panel destroys the artwork.
     */
    private void drawPoster(PdfContentByte cb, BadgeRecord rec, Map<String, byte[]> posterCache,
                            float x0, float y0, float h, float s) {
        float bx = X(x0, M_L, s);
        float by = Yt(y0, h, POSTER_B, s);
        float bw = CONTENT_W * MM * s;
        float bh = (POSTER_B - POSTER_T) * MM * s;

        Path poster = resolvePoster(rec.eventTitle());
        if (poster != null) {
            try {
                // Empty array = "we already tried and failed" sentinel, so a
                // broken poster file is not re-attempted 200 times per batch.
                byte[] bytes = posterCache.computeIfAbsent(poster.toString(), k -> {
                    byte[] b = scaledPosterBytes(poster);
                    return b == null ? new byte[0] : b;
                });
                if (bytes.length == 0) throw new IllegalStateException("unreadable poster");
                Image img = Image.getInstance(bytes);
                float iw = img.getWidth(), ih = img.getHeight();
                float scale = Math.min(bw / iw, bh / ih);      // contain
                float dw = iw * scale, dh = ih * scale;
                float dx = bx + (bw - dw) / 2f;                // centre
                float dy = by + (bh - dh) / 2f;
                img.scaleAbsolute(dw, dh);
                img.setAbsolutePosition(dx, dy);
                cb.addImage(img);
                // hairline frame around the artwork, not around the band
                cb.setColorStroke(HAIRLINE);
                cb.setLineWidth(0.6f * s);
                cb.rectangle(dx, dy, dw, dh);
                cb.stroke();
                return;
            } catch (Exception ignore) {
                // fall through to the coloured placeholder
            }
        }
        // placeholder: navy panel + centred title so the invitation is still usable
        cb.setColorFill(BLUE);
        cb.rectangle(bx, by, bw, bh);
        cb.fill();
        text(cb, bfBold, 18f * s, WHITE, Element.ALIGN_CENTER,
                safe(rec.eventTitle(), "FIH 2026"), bx + bw / 2f, by + bh / 2f);
    }

    /**
     * Reads the poster ONCE and returns the bytes to embed in the PDFs:
     * - anything else (big JPEG, any PNG) -> downscaled to POSTER_MAX_PX and
     *   re-encoded as JPEG (PNG transparency is flattened onto white);
     * - unreadable by ImageIO (e.g. CMYK JPEG) -> raw file bytes so OpenPDF
     *   can still try its own decoder, exactly like the old code path;
     * - I/O failure -> null (the caller draws the coloured placeholder).
     */
    private byte[] scaledPosterBytes(Path poster) {
        try {
            int maxPx = props.getPosterEmbedMaxPx();
            float quality = props.getPosterEmbedQuality();
            String name = poster.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean isJpeg = name.endsWith(".jpg") || name.endsWith(".jpeg");
            BufferedImage src;
            try {
                src = ImageIO.read(poster.toFile());
            } catch (Exception decodeFailure) {
                src = null;
            }
            if (src == null) {
                return Files.readAllBytes(poster);      // let OpenPDF try, as before
            }
            if (isJpeg && src.getWidth() <= maxPx) {
                return Files.readAllBytes(poster);      // already small enough
            }
            float scale = Math.min(1f, maxPx / (float) src.getWidth());
            int w = Math.max(1, Math.round(src.getWidth() * scale));
            int hh = Math.max(1, Math.round(src.getHeight() * scale));
            BufferedImage dst = new BufferedImage(w, hh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setColor(Color.WHITE);                    // flatten PNG alpha
            g.fillRect(0, 0, w, hh);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, hh, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(dst, null, null), param);
            } finally {
                writer.dispose();
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Full-bleed horizontal band between two top-anchored mm offsets. */
    private void fillBand(PdfContentByte cb, float x0, float y0, float w, float h, float s,
                          float tmm, float bmm, Color c) {
        cb.setColorFill(c);
        float yTop = Yt(y0, h, tmm, s);
        float yBot = Yt(y0, h, bmm, s);
        cb.rectangle(x0, yBot, w, yTop - yBot);
        cb.fill();
    }

    /** Filled rounded card from (lmm,tmm) to (rmm,bmm) with an optional hairline border. */
    private void card(PdfContentByte cb, float x0, float y0, float h, float s,
                      float lmm, float tmm, float rmm, float bmm, Color fill, Color border) {
        float x = X(x0, lmm, s);
        float yTop = Yt(y0, h, tmm, s);
        float yBot = Yt(y0, h, bmm, s);
        cb.setColorFill(fill);
        cb.roundRectangle(x, yBot, (rmm - lmm) * MM * s, (yTop - yBot), CARD_RADIUS * MM * s);
        cb.fill();
        if (border != null) {
            cb.setColorStroke(border);
            cb.setLineWidth(0.6f * s);
            cb.roundRectangle(x, yBot, (rmm - lmm) * MM * s, (yTop - yBot), CARD_RADIUS * MM * s);
            cb.stroke();
        }
    }

    /** Thin horizontal rule between two x offsets at a top-anchored y. */
    private void rule(PdfContentByte cb, float x0, float y0, float h, float s,
                      float lmm, float rmm, float ymm, Color c, float widthPt) {
        cb.saveState();
        cb.setColorStroke(c);
        cb.setLineWidth(widthPt * s);
        cb.moveTo(X(x0, lmm, s), Yt(y0, h, ymm, s));
        cb.lineTo(X(x0, rmm, s), Yt(y0, h, ymm, s));
        cb.stroke();
        cb.restoreState();
    }

    /** Small letter-spaced uppercase label (grey). */
    private void eyebrow(PdfContentByte cb, float x0, float y0, float h, float s,
                         String label, float xmm, float ymm) {
        cb.beginText();
        cb.setFontAndSize(bfBold, 7f * s);
        cb.setColorFill(GREY);
        cb.setCharacterSpacing(1.1f * s);
        cb.showTextAligned(Element.ALIGN_LEFT, label, X(x0, xmm, s), Yt(y0, h, ymm, s), 0);
        cb.setCharacterSpacing(0f);
        cb.endText();
    }

    /** Eyebrow label + navy value, stacked, fitted to a column width in mm. */
    private void pair(PdfContentByte cb, float x0, float y0, float h, float s,
                      String label, String value, float xmm, float ymm, float colWmm) {
        eyebrow(cb, x0, y0, h, s, label, xmm, ymm);
        float size = fitFont(bfBold, value, 11.5f * s, 6.5f * s, colWmm * MM * s);
        text(cb, bfBold, size, NAVY, Element.ALIGN_LEFT, value, X(x0, xmm, s), Yt(y0, h, ymm + 6.4f, s));
    }

    /** Horizontal text; (x,y) is the baseline anchor for the given alignment. */
    private void text(PdfContentByte cb, BaseFont bf, float size, Color color,
                      int align, String txt, float x, float yBaseline) {
        cb.beginText();
        cb.setFontAndSize(bf, size);
        cb.setColorFill(color);
        cb.showTextAligned(align, txt, x, yBaseline, 0);
        cb.endText();
    }

    /** Largest size in [min,start] whose rendered width fits maxWidth. */
    private float fitFont(BaseFont bf, String txt, float start, float min, float maxWidth) {
        float size = start;
        while (size > min && bf.getWidthPoint(txt, size) > maxWidth) {
            size -= 0.5f;
        }
        return size;
    }

    // local-mm -> page-point conversions (top-anchored)
    private float X(float x0, float mm, float s) {
        return x0 + mm * MM * s;
    }

    private float Yt(float y0, float h, float mmFromTop, float s) {
        return y0 + h - mmFromTop * MM * s;
    }

    /** Build a QR code as a 1-bit (black/white) image — OpenPDF stores it as a
     *  compact bilevel raster rather than 24-bit RGB. */
    private BufferedImage qrImage(String text, int size) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return img;
    }


    private BufferedImage barcodeImage(String text, int width, int height) throws Exception {
        BitMatrix matrix = new Code128Writer().encode(text, BarcodeFormat.CODE_128, width, height);
        int w = matrix.getWidth(), hh = matrix.getHeight();
        BufferedImage img = new BufferedImage(w, hh, BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < hh; y++) {
                img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return img;
    }

    /** Code128 is a Latin-1 symbology: reject anything it cannot encode. */
    private String barcodePayload(BadgeRecord rec) {
        String code = (rec.codebarre() != null && !rec.codebarre().isBlank())
                ? rec.codebarre().trim() : rec.numeroserie();
        if (code == null || code.isBlank()) return null;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) > 0xFF) return null;
        }
        return code;
    }

    /** "8336430" -> "8 3 3 6 4 3 0" for the human-readable line under the bars. */
    private String spaced(String code) {
        StringBuilder sb = new StringBuilder(code.length() * 2);
        for (int i = 0; i < code.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }


    private Path resolvePoster(String title) {
        String dir = props.getPosterDir();
        if (dir == null || dir.isBlank()) return null;
        Path base = Paths.get(dir);
        if (title != null && !title.isBlank()) {
            String slug = slugify(title);
            for (String ext : props.getPosterExtensions()) {
                Path p = base.resolve(slug + "." + ext);
                if (Files.isReadable(p)) return p;
            }
        }
        String def = props.getPosterDefault();
        if (def != null && !def.isBlank()) {
            Path p = base.resolve(def);
            if (Files.isReadable(p)) return p;
        }
        return null;
    }

    /** "Salif Keïta" -> "salif-keita" (accent-folded, lower-case, dash-joined). */
    private String slugify(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return n;
    }

    /** "samedi 4 juillet 2026" -> "Samedi 4 juillet 2026". */
    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String displayName(BadgeRecord rec) {
        if (rec.affecteeA() != null && !rec.affecteeA().isBlank()) return rec.affecteeA().trim();
        if (rec.holderName() != null && !rec.holderName().isBlank()) return rec.holderName().trim();
        return null;
    }

    private String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
