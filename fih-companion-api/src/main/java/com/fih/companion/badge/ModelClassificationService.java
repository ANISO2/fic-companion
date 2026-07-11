package com.fih.companion.badge;

import com.fih.companion.diagnostics.ConsoleLog;
import com.fih.companion.repository.ModeleBilletRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;


@Service
public class ModelClassificationService {

    private final ModeleBilletRepository modeleRepo;
    private final BadgeProperties badgeProperties;

    private volatile Set<Integer> paidModelCache;
    private volatile Set<Integer> invitationTypeCache;

    public ModelClassificationService(ModeleBilletRepository modeleRepo,
                                      BadgeProperties badgeProperties) {
        this.modeleRepo = modeleRepo;
        this.badgeProperties = badgeProperties;
    }

    /** True when the model is sold (vente=true) — excluded from this section. */
    public boolean isPaid(Integer modelId) {
        return modelId != null && paidModels().contains(modelId);
    }

    /** True when the model may be assigned a name here (any non-paid model). */
    public boolean isAffectable(Integer modelId) {
        return modelId != null && !isPaid(modelId);
    }

    /** True when the model may also be printed (configured invitation models). */
    public boolean isPrintable(Integer modelId) {
        return badgeProperties.isInvitationModel(modelId);
    }

    /**
     * Chantier 2 — INVITATION | BADGE | ACCES. Unique source de vérité du
     * découpage des pages du backoffice, lue depuis
     * {@code fih.badge.model-categories}. Jamais null.
     */
    public String category(Integer modelId) {
        return badgeProperties.categoryOf(modelId);
    }

    /**
     * CORRECTION — une VRAIE invitation : son libellé contient « invitation ».
     *
     * <p>C'est ce prédicat, et lui seul, qui décide du mécanisme de visibilité
     * appliqué à un compte non-admin :</p>
     * <ul>
     *   <li><b>invitation</b> → visibilité par LOT (contingent). L'utilisateur
     *       ne voit que le sous-ensemble qui lui a été explicitement affecté.</li>
     *   <li><b>tout le reste</b> (Badge, Kit, Accès) → visibilité BINAIRE par
     *       permission. Si le type est coché, il voit la TOTALITÉ de ce type
     *       pour l'événement. Aucune notion de quantité, aucun lot.</li>
     * </ul>
     *
     * <p>Lu en base, pas en configuration : un Kit renommé restera un Kit.</p>
     */
    public boolean isInvitationType(Integer modelId) {
        return modelId != null && invitationTypes().contains(modelId);
    }

    private Set<Integer> invitationTypes() {
        Set<Integer> cache = this.invitationTypeCache;
        if (cache == null) {
            synchronized (this) {
                cache = this.invitationTypeCache;
                if (cache == null) {
                    cache = new HashSet<>(modeleRepo.findInvitationModelReferences());
                    this.invitationTypeCache = cache;

                    // Garde-fou : l'imprimabilité vient de la config, la
                    // visibilité vient du nom en base. Les deux DOIVENT
                    // coïncider. Si elles divergent, un Kit redeviendrait
                    // imprimable sans que personne ne le voie.
                    Set<Integer> configured = badgeProperties.invitationModelSet();
                    if (!cache.equals(new HashSet<>(configured))) {
                        ConsoleLog.log("BADGE", "AVERTISSEMENT — divergence de classification : "
                                + "modèles nommés « Invitation » en base = " + cache
                                + ", mais fih.badge.model-categories.INVITATION = " + configured
                                + ". Les modèles imprimables devraient être exactement les "
                                + "invitations. Corrigez application.yml.");
                    } else {
                        ConsoleLog.log("BADGE", "classification des invitations OK — "
                                + "types nommés « Invitation » = " + cache
                                + " (visibilité par LOT ; tous les autres types : visibilité "
                                + "binaire par permission).");
                    }
                }
            }
        }
        return cache;
    }

    private Set<Integer> paidModels() {
        Set<Integer> cache = this.paidModelCache;
        if (cache == null) {
            synchronized (this) {
                cache = this.paidModelCache;
                if (cache == null) {
                    cache = new HashSet<>(modeleRepo.findPaidModelReferences());
                    this.paidModelCache = cache;
                    ConsoleLog.log("BADGE", "model classification loaded — PAID models (vente=true, excluded "
                            + "from Invitations & Badges) = " + cache
                            + "; PRINTABLE models (fih.badge.invitation-models) = "
                            + badgeProperties.invitationModelSet() + ".");
                }
            }
        }
        return cache;
    }
}
