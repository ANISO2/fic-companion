import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Router } from '@angular/router';

interface NavItem { label: string; icon: string; path: string; }

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="min-h-screen flex">
      <!-- Barre latérale -->
      <aside
        class="fixed top-0 left-0 z-40 h-screen w-64 shrink-0 flex flex-col text-white transition-transform duration-200"
        [style.background]="'linear-gradient(180deg, var(--primary-700) 0%, #0e1633 100%)'"
        [class.-translate-x-full]="!open()"
        [class.lg:translate-x-0]="true">
        <div class="px-5 py-5 flex items-center gap-3 border-b border-white/10">
          <span class="msr text-2xl" [style.color]="'var(--accent)'">theater_comedy</span>
          <div class="leading-tight">
            <div class="font-bold tracking-wide">FIC</div>
            <div class="text-[11px] text-white/55 leading-tight">Festival International<br />de Carthage</div>
          </div>
        </div>

        <nav class="flex-1 min-h-0 overflow-y-auto px-3 py-4 space-y-1 nav-scroll">
          @for (item of visibleNav(); track item.path) {
            <a [routerLink]="item.path" routerLinkActive="nav-active"
               [routerLinkActiveOptions]="{ exact: item.path === '' }"
               (click)="closeOnMobile()"
               class="flex items-center gap-3 px-3 py-2.5 rounded-xl text-white/75 hover:bg-white/10 hover:text-white transition-colors">
              <span class="msr text-[20px]">{{ item.icon }}</span>
              <span class="text-sm font-medium">{{ item.label }}</span>
            </a>
          }
        </nav>

        <div class="px-4 py-4 border-t border-white/10">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-9 h-9 rounded-full bg-white/15 grid place-items-center font-semibold">
              {{ initials() }}
            </div>
            <div class="leading-tight min-w-0">
              <div class="text-sm font-medium truncate">{{ auth.displayName() || 'Admin' }}</div>
              <div class="text-xs text-white/50 truncate">{{ auth.role() }}</div>
            </div>
          </div>
          <button (click)="logout()"
                  class="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl bg-white/10 hover:bg-white/20 text-sm font-medium transition-colors">
            <span class="msr text-[18px]">logout</span> Déconnexion
          </button>
        </div>
      </aside>

      <!-- Fond sombre sur mobile -->
      @if (open()) {
        <div class="fixed inset-0 bg-black/30 z-30 lg:hidden" (click)="open.set(false)"></div>
      }

      <!-- Contenu principal -->
      <div class="flex-1 min-w-0 flex flex-col lg:ml-64">
        <header class="sticky top-0 z-20 bg-surface border-b border-line shadow-[0_1px_3px_rgba(16,30,54,.08)]">
          <div class="px-4 sm:px-6 lg:px-8 h-16 flex items-center gap-3">
            <button class="lg:hidden p-2 -ml-2 rounded-lg hover:bg-line" (click)="open.set(true)"
                    aria-label="Ouvrir le menu">
              <span class="msr">menu</span>
            </button>
            <h1 class="text-lg font-semibold text-ink">{{ auth.isInvitationsOnly() ? 'FIC · Invitations & Badges' : auth.isGestion() ? 'FIC · Gestion' : 'FIC · Statistiques' }}</h1>

            <div class="flex-1"></div>
          </div>
        </header>
        <main class="flex-1 px-4 sm:px-6 lg:px-8 py-6 max-w-[1400px] w-full mx-auto">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: [`
    /* Barre de defilement discrete pour la navigation, adaptee au fond sombre.
       La regle globale de styles.css (claire) jurerait ici. */
    .nav-scroll { scrollbar-width: thin; scrollbar-color: rgba(255,255,255,.22) transparent; }
    .nav-scroll::-webkit-scrollbar { width: 6px; }
    .nav-scroll::-webkit-scrollbar-track { background: transparent; }
    .nav-scroll::-webkit-scrollbar-thumb {
      background: rgba(255,255,255,.18);
      border-radius: 999px;
    }
    .nav-scroll::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,.32); }
  `],
})
export class ShellComponent {
  open = signal(false);
  nav: NavItem[] = [
    { label: 'Vue d\'ensemble', icon: 'dashboard', path: '' },
    { label: 'Événements', icon: 'event', path: 'events' },
    { label: 'Recette', icon: 'payments', path: 'recette' },
    { label: 'Recette par guichet', icon: 'storefront', path: 'recette-guichet' },
    { label: 'Tourniquets', icon: 'meeting_room', path: 'tourniquets' },
    { label: 'Analyse des rejets', icon: 'report', path: 'rejets' },
    { label: 'Vérification billet', icon: 'confirmation_number', path: 'verification/billet' },
    { label: 'Vérification voucher', icon: 'local_activity', path: 'verification/voucher' },
    // Chantier 2 (révisé) — deux entrées séparées dans la Navbar au lieu
    // d'un seul « Invitations & Badges ».
    { label: 'Invitations', icon: 'mail', path: 'invitations' },
    { label: 'Badges', icon: 'badge', path: 'badges' },
    // Chantier 3 — réservé à l'administrateur (filtré par visibleNav()).
    { label: 'Utilisateurs', icon: 'manage_accounts', path: 'admin/utilisateurs' },
    { label: "Lots d'invitations", icon: 'inventory_2', path: 'admin/lots' },
    { label: "Types d'acces", icon: 'sensor_door', path: 'acces' },
    { label: 'Tourniquets', icon: 'meeting_room', path: 'gates' }
  ];

  constructor(public auth: AuthService, private router: Router) {}

  /**
   * Feature 1 — the restricted "Invitations & Badges only" account sees just
   * that single nav item; everyone else sees the full menu.
   */
  visibleNav(): NavItem[] {
    if (this.auth.isInvitationsOnly()) {
      // Chantier 2 (révisé) — un compte restreint garde les deux pages.
      return this.nav.filter(item => item.path === 'invitations' || item.path === 'badges' || item.path === 'acces');
    }
    // Compte « Gestion » : Invitations, Badges, Utilisateurs, Lots d'invitations.
    if (this.auth.isGestion()) {
      return this.nav.filter(item => item.path === 'invitations' || item.path === 'badges'
        || item.path === 'admin/utilisateurs' || item.path === 'admin/lots');
    }
    return this.nav;
  }

  initials(): string {
    const n = this.auth.displayName() || 'A';
    return n.split(' ').map(p => p[0]).slice(0, 2).join('').toUpperCase();
  }
  closeOnMobile(): void { if (window.innerWidth < 1024) this.open.set(false); }
  logout(): void { this.auth.logout(); this.router.navigate(['/login']); }
}