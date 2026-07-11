/**
 * Traduit une erreur HttpErrorResponse en un message court, en français,
 * compréhensible par un non-technicien.
 *
 * Aucun code HTTP nu, aucun nom de table, aucune trace serveur ne doit
 * remonter à l'écran. Le message du serveur n'est repris QUE s'il est
 * manifestement destiné à l'humain : court, en français, sans jargon.
 */
export function humanizeError(err: unknown, fallback = 'Une erreur est survenue.'): string {
  const e = err as { status?: number; error?: { message?: string; detail?: string } } | null;
  if (!e) return fallback;

  // 0 = pas de réponse : réseau coupé, API arrêtée, CORS.
  if (e.status === 0) return 'Le serveur est injoignable. Vérifiez votre connexion.';

  const raw = e.error?.message ?? e.error?.detail ?? '';
  if (isHumanMessage(raw)) return raw;

  switch (e.status) {
    case 400: return 'La demande est incomplète ou mal formée.';
    case 401: return 'Votre session a expiré. Reconnectez-vous.';
    case 403: return "Vous n'avez pas les droits pour cette action.";
    case 404: return 'Cet élément est introuvable.';
    case 409: return 'Cette action est en conflit avec une donnée existante.';
    case 422: return "Cette action n'est pas possible dans l'état actuel.";
    case 429: return 'Trop de demandes. Patientez un instant.';
    case 500:
    case 502:
    case 503:
    case 504: return 'Le serveur rencontre un problème. Réessayez dans un instant.';
    default: return fallback;
  }
}

/**
 * Un message serveur est « humain » s'il est court, ponctué, et ne contient
 * aucun marqueur technique. Sinon on préfère un message générique : mieux vaut
 * vague que d'exposer `badge_affectation` ou une stack Java à l'utilisateur.
 */
function isHumanMessage(msg: string): boolean {
  // Plafond porté de 180 à 260 : les messages précis de création de lot
  // (« Ce numéro est déjà confié à Prénom Nom (username)… », listes de numéros)
  // dépassent parfois 180 caractères. Sous 180 ils étaient remplacés par un
  // message générique — l'admin ne savait alors PLUS ce qui bloquait. Le filtre
  // « technique » ci-dessous reste la vraie barrière contre les fuites de stack.
  if (!msg || msg.length < 8 || msg.length > 260) return false;
  const technical = /(exception|error:|\bsql\b|constraint|nativequery|org\.|com\.fih|_[a-z]+_|stack|null pointer|\bat [a-z]+\.[a-z]+\()/i;
  if (technical.test(msg)) return false;
  // Un vrai message métier finit par une ponctuation et contient un espace.
  return /\s/.test(msg) && /[.!?»)]$/.test(msg.trim());
}
