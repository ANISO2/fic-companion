import { Routes } from '@angular/router';
import { authGuard, invitationsRouteGuard } from './core/auth.guard';
import { ShellComponent } from './layout/shell.component';
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.component').then(m => m.LoginComponent) },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [invitationsRouteGuard],
    children: [
      { path: '', loadComponent: () => import('./pages/overview.component').then(m => m.OverviewComponent) },
      { path: 'events', loadComponent: () => import('./pages/events.component').then(m => m.EventsComponent) },
      { path: 'events/:id', loadComponent: () => import('./pages/event-detail.component').then(m => m.EventDetailComponent) },
      { path: 'recette', loadComponent: () => import('./pages/recette.component').then(m => m.RecetteComponent) },
      { path: 'recette-guichet', loadComponent: () => import('./pages/recette-guichet.component').then(m => m.RecetteGuichetComponent) },
      { path: 'tourniquets', loadComponent: () => import('./pages/tourniquets.component').then(m => m.TourniquetsComponent) },
      { path: 'verification/billet', loadComponent: () => import('./pages/verification-billet.component').then(m => m.VerificationBilletComponent) },
      { path: 'verification/voucher', loadComponent: () => import('./pages/verification-voucher.component').then(m => m.VerificationVoucherComponent) },
      { path: 'rejets', loadComponent: () => import('./pages/rejets.component').then(m => m.RejetsComponent) },
      { path: 'gates', loadComponent: () => import('./pages/gates.component').then(m => m.GatesComponent) },
      // Chantier 2 (révisé) — deux pages distinctes dans la barre latérale,
      // au lieu d'un seul écran "Invitations & Badges". Chacune reprend le
      // layout d'origine, filtrée par catégorie (fih.badge.model-categories,
      // seule source de vérité). L'écran de détail est un frère, pas un
      // enfant : il reste atteignable depuis les deux listes.
      { path: 'invitations', loadComponent: () => import('./pages/invitations.component').then(m => m.InvitationsComponent) },
      { path: 'badges', loadComponent: () => import('./pages/badges.component').then(m => m.BadgesComponent) },
      { path: 'acces', loadComponent: () => import('./pages/acces.component').then(m => m.AccesComponent) },
      { path: 'badges/:eventId/:modelId', loadComponent: () => import('./pages/badge-detail.component').then(m => m.BadgeDetailComponent) },
      // Chantier 3 — « Gestion des rôles ». L'API refuse déjà /api/admin/**
      // hors ROLE_ADMIN ; invitationsRouteGuard est la seconde barrière, pas
      // la première.
      { path: 'admin/utilisateurs', loadComponent: () => import('./pages/admin/admin-users.component').then(m => m.AdminUsersComponent) },
      { path: 'admin/lots', loadComponent: () => import('./pages/admin/admin-contingents.component').then(m => m.AdminContingentsComponent) }
    ]
  },
  { path: '**', redirectTo: '' }
];
