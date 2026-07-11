package com.fih.companion.badge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "fih.badge")
public class BadgeProperties {

    private String photoDir = "D:/Bitaka/bitaka/fih-companion/photos";
    private String photoKey = "codebarre";
    private List<String> photoExtensions = List.of("jpg", "jpeg", "png");

    private double widthMm = 100;
    private double heightMm = 150;
    private int zipThreshold = 150;

    // ===================== e-ticket layout =====================
    // A4 portrait. The page size stays config-driven (ticketPage() reads these),
    // it is simply no longer a 220x100 landscape stub.
    private double ticketWidthMm = 210;
    private double ticketHeightMm = 297;

    /** Static event venue printed in the « Lieu » field of the invitation. */
    private String venueName = "Th\u00e9\u00e2tre Antique de Carthage";


    private String posterDir = "D:/Bitaka/bitaka/fih-companion/posters";
    private String posterDefault = "poster-default.jpg";
    private List<String> posterExtensions = List.of("jpg", "jpeg", "png");

    // ---- poster embedding budget (drives per-badge PDF size) ----
    // The poster JPEG is by far the largest thing inside each ticket PDF, and
    // it is re-embedded in every badge. The panel is only ~100 mm wide, so a
    // huge source poster is pure waste. These two knobs cap what gets embedded:
    //   posterEmbedMaxPx  — longest edge in pixels before downscaling
    //                       (600 px over 100 mm ≈ 150 dpi, plenty for a ticket).
    //   posterEmbedQuality — JPEG quality 0..1 for the re-encoded poster.
    // Lower either to shrink the ZIP further (e.g. 450 px / 0.65 ≈ 25 KB/badge).
    private int posterEmbedMaxPx = 600;
    private float posterEmbedQuality = 0.72f;


    private String showTime = "22:00";
    private Map<String, String> showTimes = new LinkedHashMap<>();
    // ===========================================================

    /**
     * Chantier 1/2 — LA source de vérité du classement des modèles.
     * Clés attendues : INVITATION, BADGE, ACCES. Tout modèle absent de la map
     * est traité comme ACCES.
     *
     * <p>{@code invitationModels} ci-dessous n'est plus qu'un REPLI : si la map
     * est vide (ancienne configuration), on retombe sur la liste plate. Il n'y a
     * donc jamais deux mécanismes actifs en même temps.</p>
     */
    private Map<String, List<Integer>> modelCategories = new LinkedHashMap<>();

    /** Repli historique (fih.badge.invitation-models). Ignoré si model-categories est renseigné. */
    private String invitationModels = "3,36,38,39,40,41";

    private Set<Integer> invitationModelCache;
    private Map<Integer, String> categoryCache;

    public static final String CAT_INVITATION = "INVITATION";
    public static final String CAT_BADGE = "BADGE";
    public static final String CAT_ACCES = "ACCES";

    public String getPhotoDir() { return photoDir; }
    public void setPhotoDir(String photoDir) { this.photoDir = photoDir; }
    public String getPhotoKey() { return photoKey; }
    public void setPhotoKey(String photoKey) { this.photoKey = photoKey; }
    public List<String> getPhotoExtensions() { return photoExtensions; }
    public void setPhotoExtensions(List<String> photoExtensions) { this.photoExtensions = photoExtensions; }

    public double getWidthMm() { return widthMm; }
    public void setWidthMm(double widthMm) { this.widthMm = widthMm; }
    public double getHeightMm() { return heightMm; }
    public void setHeightMm(double heightMm) { this.heightMm = heightMm; }
    public int getZipThreshold() { return zipThreshold; }
    public void setZipThreshold(int zipThreshold) { this.zipThreshold = zipThreshold; }

    public double getTicketWidthMm() { return ticketWidthMm; }
    public void setTicketWidthMm(double ticketWidthMm) { this.ticketWidthMm = ticketWidthMm; }
    public double getTicketHeightMm() { return ticketHeightMm; }
    public void setTicketHeightMm(double ticketHeightMm) { this.ticketHeightMm = ticketHeightMm; }

    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }

    public String getPosterDir() { return posterDir; }
    public void setPosterDir(String posterDir) { this.posterDir = posterDir; }
    public String getPosterDefault() { return posterDefault; }
    public void setPosterDefault(String posterDefault) { this.posterDefault = posterDefault; }
    public List<String> getPosterExtensions() { return posterExtensions; }
    public void setPosterExtensions(List<String> posterExtensions) { this.posterExtensions = posterExtensions; }

    public int getPosterEmbedMaxPx() { return posterEmbedMaxPx; }
    public void setPosterEmbedMaxPx(int posterEmbedMaxPx) {
        // guard against 0/negative from a bad env value; keep at least 64 px.
        this.posterEmbedMaxPx = Math.max(64, posterEmbedMaxPx);
    }
    public float getPosterEmbedQuality() { return posterEmbedQuality; }
    public void setPosterEmbedQuality(float posterEmbedQuality) {
        // JPEG quality must stay in (0,1]; clamp anything out of range.
        if (posterEmbedQuality < 0.1f) posterEmbedQuality = 0.1f;
        if (posterEmbedQuality > 1f) posterEmbedQuality = 1f;
        this.posterEmbedQuality = posterEmbedQuality;
    }

    public String getShowTime() { return showTime; }
    public void setShowTime(String showTime) { this.showTime = showTime; }
    public Map<String, String> getShowTimes() { return showTimes; }
    public void setShowTimes(Map<String, String> showTimes) { this.showTimes = showTimes; }

    public String resolveShowTime(LocalDate date) {
        if (date != null && showTimes != null) {
            String v = showTimes.get(date.toString()); // yyyy-MM-dd
            if (v != null && !v.isBlank()) return v;
        }
        return showTime;
    }

    public Map<String, List<Integer>> getModelCategories() { return modelCategories; }
    public void setModelCategories(Map<String, List<Integer>> modelCategories) {
        this.modelCategories = modelCategories == null ? new LinkedHashMap<>() : modelCategories;
        this.invitationModelCache = null;   // les deux caches dérivent de la map
        this.categoryCache = null;
    }

    public String getInvitationModels() { return invitationModels; }
    public void setInvitationModels(String invitationModels) {
        this.invitationModels = invitationModels;
        this.invitationModelCache = null; // re-parse on next access
    }

    /**
     * INVITATION | BADGE | ACCES. Jamais null : un modèle inconnu de la map
     * tombe dans ACCES (« le reste »).
     */
    public String categoryOf(Integer modelId) {
        if (modelId == null) return CAT_ACCES;
        return categoryMap().getOrDefault(modelId, CAT_ACCES);
    }

    private Map<Integer, String> categoryMap() {
        Map<Integer, String> cache = this.categoryCache;
        if (cache == null) {
            cache = new LinkedHashMap<>();
            for (Map.Entry<String, List<Integer>> e : modelCategories.entrySet()) {
                String cat = e.getKey() == null ? CAT_ACCES : e.getKey().trim().toUpperCase();
                if (e.getValue() == null) continue;
                for (Integer id : e.getValue()) {
                    if (id != null) cache.put(id, cat);
                }
            }
            // Repli : sans map, seuls les invitation-models sont classés.
            if (cache.isEmpty()) {
                for (Integer id : parseInvitationModels()) cache.put(id, CAT_INVITATION);
            }
            this.categoryCache = cache;
        }
        return cache;
    }

    public boolean isInvitationModel(Integer modelId) {
        return modelId != null && invitationModelSet().contains(modelId);
    }

    /**
     * Les modèles IMPRIMABLES. DÉRIVÉ de model-categories.INVITATION : une seule
     * source de vérité. Sans map configurée, repli sur invitation-models.
     */
    public Set<Integer> invitationModelSet() {
        Set<Integer> cache = this.invitationModelCache;
        if (cache == null) {
            cache = new LinkedHashSet<>();
            List<Integer> fromMap = modelCategories.get(CAT_INVITATION);
            if (fromMap != null && !fromMap.isEmpty()) {
                for (Integer id : fromMap) if (id != null) cache.add(id);
            } else {
                cache.addAll(parseInvitationModels());
            }
            this.invitationModelCache = cache;
        }
        return cache;
    }

    /** Parse de la clé plate historique. Utilisé uniquement en repli. */
    private Set<Integer> parseInvitationModels() {
        Set<Integer> out = new LinkedHashSet<>();
        if (invitationModels != null && !invitationModels.isBlank()) {
            for (String token : invitationModels.split("[,;\\s]+")) {
                String t = token.trim();
                if (t.isEmpty()) continue;
                try {
                    out.add(Integer.valueOf(t));
                } catch (NumberFormatException ignored) {
                    // Skip anything that isn't an integer rather than failing startup.
                }
            }
        }
        return out;
    }

    public List<Integer> invitationModelList() {
        return Arrays.asList(invitationModelSet().toArray(new Integer[0]));
    }
}
