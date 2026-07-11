/**
 * Couleur de la pastille d'une zone d'accès.
 * P = Public, V = Vip, R = Press (cf. AccessZoneResolver côté API).
 * Utilisé par les pages Invitations et Badges.
 */
export function zoneColor(z: string): string {
  const v = (z || '').toLowerCase();
  if (v.startsWith('vip') || v === 'v') return 'var(--warn)';
  if (v.startsWith('press') || v === 'r') return '#5b3aa6';
  return 'var(--success)';
}
