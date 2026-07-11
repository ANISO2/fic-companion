import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AppUser, CreateUserRequest, UpdateUserRequest, ResetPasswordRequest,
  ModelPermissionsRequest, ModelOption,
  Contingent, CreateContingentRequest, ContingentLigne, Disponibilite,
  EventOption
} from './models';

/**
 * Chantier 3 — « Gestion des rôles ». Toutes ces routes sont ADMIN uniquement,
 * la règle est appliquée côté serveur (SecurityConfig sur /api/admin/**).
 * Le masquage côté Angular n'est qu'un confort, il ne protège rien.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {
  constructor(private http: HttpClient) {}

  // ---------------------------------------------------------------- comptes
  users(): Observable<AppUser[]> {
    return this.http.get<AppUser[]>('/api/admin/users');
  }

  /** Les 35 types, pour la grille d'interrupteurs. */
  modelOptions(): Observable<ModelOption[]> {
    return this.http.get<ModelOption[]>('/api/admin/users/model-options');
  }

  createUser(req: CreateUserRequest): Observable<AppUser> {
    return this.http.post<AppUser>('/api/admin/users', req);
  }

  updateUser(id: number, req: UpdateUserRequest): Observable<AppUser> {
    return this.http.put<AppUser>(`/api/admin/users/${id}`, req);
  }

  resetPassword(id: number, req: ResetPasswordRequest): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/password`, req);
  }

  /** Remplace la liste COMPLÈTE des types visibles. Liste vide = plus rien. */
  setModels(id: number, modelIds: number[]): Observable<AppUser> {
    const req: ModelPermissionsRequest = { modelIds };
    return this.http.put<AppUser>(`/api/admin/users/${id}/models`, req);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/users/${id}`);
  }

  // ------------------------------------------------------------ contingents
  contingents(userId?: number): Observable<Contingent[]> {
    const qs = userId != null ? `?userId=${userId}` : '';
    return this.http.get<Contingent[]>(`/api/admin/contingents${qs}`);
  }

  contingentLignes(id: number): Observable<ContingentLigne[]> {
    return this.http.get<ContingentLigne[]>(`/api/admin/contingents/${id}/lignes`);
  }

  disponibilite(eventId: number, modelId: number): Observable<Disponibilite> {
    return this.http.get<Disponibilite>(
      `/api/admin/contingents/disponibilite?eventId=${eventId}&modelId=${modelId}`);
  }

  createContingent(req: CreateContingentRequest): Observable<Contingent> {
    return this.http.post<Contingent>('/api/admin/contingents', req);
  }

  /** Le bénéficiaire ne voit plus ses lignes. Les noms déjà posés restent. */
  revoke(id: number): Observable<Contingent> {
    return this.http.post<Contingent>(`/api/admin/contingents/${id}/revocation`, {});
  }

  /** Suppression définitive. 409 si une invitation est déjà nommée. */
  deleteContingent(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/contingents/${id}`);
  }

  // ----------------------------------------------------------------- divers
  events(): Observable<EventOption[]> {
    return this.http.get<EventOption[]>('/api/events');
  }
}
