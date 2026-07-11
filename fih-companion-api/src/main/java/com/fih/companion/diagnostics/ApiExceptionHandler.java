package com.fih.companion.diagnostics;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filet de sécurité GLOBAL : toute exception qui remonte d'un contrôleur est
 * transformée en une réponse JSON propre {@code {status, message}}, en français,
 * que le front peut TOUJOURS afficher.
 *
 * <p><b>Pourquoi ce fichier existe.</b> Sans lui, une erreur non anticipée
 * (typiquement la violation de la clé primaire {@code contingent_ligne.numeroserie}
 * quand on tente de livrer un numéro déjà livré) remontait en 500 « brut »
 * — parfois interprété côté client comme une session morte, d'où la
 * DÉCONNEXION AUTOMATIQUE et l'absence de message. Ici, chaque cas reçoit un
 * statut correct et un message lisible ; l'intercepteur Angular ne déconnecte
 * plus que sur un vrai 401.</p>
 *
 * <p>Les exceptions de la chaîne de sécurité (401/403 émis AVANT le contrôleur)
 * ne passent pas par ici : elles restent gérées par Spring Security. Ce filet
 * ne touche donc jamais l'authentification réelle.</p>
 *
 * <p>Aucune fuite technique : les messages destinés à l'humain sont explicites,
 * et le détail brut (SQL, pile) part uniquement dans les logs serveur.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String TAG = "API";

    /**
     * Erreurs MÉTIER volontaires : {@code throw new ResponseStatusException(422, "…")}.
     * On PRÉSERVE le statut et on RENVOIE le message (la « reason »), qui serait
     * sinon supprimé par défaut par Spring (server.error.include-message=never).
     * C'est ce qui garantit qu'un message précis (« déjà confié à X ») arrive au front.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        String reason = ex.getReason();
        String message = (reason != null && !reason.isBlank()) ? reason : defaultFor(ex.getStatusCode());
        return body(ex.getStatusCode(), message);
    }

    /**
     * Conflit de données : violation de contrainte (clé primaire / unicité / FK).
     * Cas emblématique : un numéro de série déjà présent dans {@code contingent_ligne}.
     * On renvoie 409 avec un message clair au lieu d'un 500 opaque.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        ConsoleLog.log(TAG, "conflit d'integrite : " + shorten(rootMessage(ex)));
        return body(HttpStatus.CONFLICT,
                "Cette opération entre en conflit avec une donnée existante : un numéro de série est "
                        + "peut-être déjà attribué à un lot. Vérifiez votre saisie, puis réessayez.");
    }

    /** Corps de requête invalide au sens du Bean Validation (@Valid). On remonte le 1er message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("La demande est incomplète ou mal formée.");
        return body(HttpStatus.BAD_REQUEST, message);
    }

    /** JSON illisible / corps manquant. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "La demande est illisible ou mal formée.");
    }

    /**
     * Droits insuffisants (sécurité par méthode). Reste un 403 : ne JAMAIS le
     * laisser tomber dans le filet final qui en ferait un 500. Un 403 n'est pas
     * une session morte — l'intercepteur ne déconnecte pas dessus.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, defaultFor(HttpStatus.FORBIDDEN));
    }

    /**
     * Filet FINAL : tout le reste. Les exceptions MVC standard (405, 415, 404…)
     * portent déjà leur propre statut via {@link ErrorResponse} — on le respecte,
     * pour ne JAMAIS transformer un 404/405 en 500. Le vrai imprévu devient un 500
     * propre, et le détail brut ne part qu'aux logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            return body(er.getStatusCode(), defaultFor(er.getStatusCode()));
        }
        ConsoleLog.error(TAG, "erreur inattendue : " + ex.getClass().getSimpleName(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "Le serveur a rencontré un problème inattendu. Réessayez ; si cela persiste, "
                        + "prévenez l'administrateur.");
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("status", status.value());
        b.put("message", message);
        return ResponseEntity.status(status).body(b);
    }

    /** Message générique en français par statut, quand aucun message métier n'est fourni. */
    private static String defaultFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "La demande est incomplète ou mal formée.";
            case 401 -> "Votre session a expiré. Reconnectez-vous.";
            case 403 -> "Vous n'avez pas les droits pour cette action.";
            case 404 -> "Élément introuvable.";
            case 405 -> "Cette action n'est pas autorisée ici.";
            case 409 -> "Cette action est en conflit avec une donnée existante.";
            case 415 -> "Format de données non pris en charge.";
            case 422 -> "Cette action n'est pas possible dans l'état actuel.";
            case 429 -> "Trop de demandes. Patientez un instant.";
            default -> status.value() >= 500
                    ? "Le serveur rencontre un problème. Réessayez dans un instant."
                    : "Une erreur est survenue.";
        };
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage() == null ? ex.getClass().getSimpleName() : root.getMessage();
    }

    private static String shorten(String m) {
        if (m == null) return "(sans message)";
        return m.length() <= 200 ? m : m.substring(0, 200) + "…";
    }
}
