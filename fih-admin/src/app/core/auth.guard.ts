import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Blocks every page except /login unless a token is present. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};

 
export const invitationsRouteGuard: CanActivateChildFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const path = route.routeConfig?.path ?? '';
  // Chantier 2 (révisé) — la section "Invitations & Badges" est désormais deux
  // pages séparées (« invitations » et « badges »). Un compte restreint garde
  // accès aux deux, ainsi qu'à l'écran de détail (frère de « badges »).
  if (auth.isInvitationsOnly()) {
    const allowed = path === 'invitations' || path === 'badges' || path === 'acces' || path.startsWith('badges/');
    return allowed ? true : router.createUrlTree(['/invitations']);
  }
  // Compte « Gestion » : Invitations, Badges, Utilisateurs et Lots d'invitations
  // (+ écran de détail des badges). Tout le reste (recette, stats, vérification…)
  // est redirigé. L'API applique la même limite de son côté (seconde barrière).
  if (auth.isGestion()) {
    const allowed = path === 'invitations' || path === 'badges' || path.startsWith('badges/')
      || path === 'admin/utilisateurs' || path === 'admin/lots';
    return allowed ? true : router.createUrlTree(['/invitations']);
  }
  return true;
};
