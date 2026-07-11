import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attache le JWT a chaque requete.
 *
 * Deconnexion UNIQUEMENT si le serveur dit clairement que la SESSION est
 * invalide : un 401 (non authentifie) sur un appel authentifie. Tout le reste
 * — 403 (droits insuffisants), 409, 422 (validation metier), 5xx, ou une erreur
 * reseau (status 0) — est laisse remonter au composant, qui affiche un message.
 *
 * CORRECTION du bug « affecter deconnecte » : auparavant, une simple erreur
 * metier (422) pouvait, selon l'ordre des appels, entrainer une sortie de
 * session. Desormais SEUL un vrai 401 deconnecte, et jamais sur l'appel de
 * login lui-meme.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((err) => {
      const isAuthError = err instanceof HttpErrorResponse && err.status === 401;
      const isLoginCall = req.url.includes('/api/auth/login');

      // Ne deconnecter QUE sur un vrai 401 d'une requete authentifiee.
      // Un 403/409/422/500/0 n'a rien a voir avec la validite de la session.
      if (isAuthError && !isLoginCall && auth.isLoggedIn()) {
        auth.logout();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    })
  );
};