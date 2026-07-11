import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BadgeService } from '../core/badge.service';
import { Availability } from '../core/models';
import { LoadingSkeletonComponent } from '../shared/loading-skeleton.component';
import { EmptyStateComponent } from '../shared/empty-state.component';
import { NumPipe, GDatePipe } from '../shared/format';
import { SectionHeaderComponent } from '../shared/section-header.component';
import { zoneColor } from '../shared/zone-color';

interface EventGroup {
  eventId: number; eventTitle: string; eventDate: string; rows: Availability[];
}

/**
 * Page « Badges », séparée dans la barre latérale (Navbar).
 *
 * Reprend le layout d'origine de badges.component.ts, réduit aux modèles dont
 * `category !== 'INVITATION'` (Badges + Portes/Accès). Pas de bandeau
 * « affiches » ici : les affiches ne concernent que l'impression des
 * invitations (voir la page Invitations), ce bandeau y serait toujours vide.
 */
@Component({
  selector: 'app-badges',
  standalone: true,
  imports: [FormsModule, LoadingSkeletonComponent, EmptyStateComponent, NumPipe, GDatePipe, SectionHeaderComponent],
  template: `
    <app-section-header title="Badges" icon="badge"
                        subtitle="Badges et kits — visibilite par simple activation du type."
                        [count]="totalInjected()" />

    @if (loading()) {
      <app-loading-skeleton [height]="360" />
    } @else if (error()) {
      <app-empty-state [error]="true" title="Impossible de charger la disponibilité" message="Le serveur est peut-être indisponible." />
    } @else {
      <!-- Bandeau récapitulatif -->
      <div class="surface-card p-5 mb-6 max-w-xs">
        <div class="text-sm text-muted">Total disponible</div>
        <div class="text-3xl font-extrabold text-ink">{{ totalInjected() | num }}</div>
        <div class="text-xs text-muted mt-1">badges dans la base (ou vos lots, si accès restreint)</div>
      </div>

      <!-- Contrôles -->
      <div class="flex flex-wrap items-center gap-3 mb-4">
        <select [(ngModel)]="selectedEvent" (ngModelChange)="onEventChange()"
                class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent">
          <option [ngValue]="null">Tous les événements</option>
          @for (e of eventOptions(); track e.id) { <option [ngValue]="e.id">{{ e.title }}</option> }
        </select>
      </div>

      @if (groups().length === 0) {
        <app-empty-state icon="badge" title="Aucun badge trouvé"
                         message="Rien ne correspond à ce filtre. Si vous avez un accès restreint, vérifiez qu'un lot vous a été affecté." />
      } @else {
        @for (g of groups(); track g.eventId) {
          <div class="surface-card mb-4 overflow-hidden">
            <div class="px-5 py-3 border-b border-line flex items-center justify-between">
              <div class="font-semibold text-ink">{{ g.eventTitle }}</div>
              <div class="text-sm text-muted">{{ g.eventDate | gdate }}</div>
            </div>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-left text-muted border-b border-line">
                    <th class="px-5 py-2.5">Modèle</th>
                    <th class="px-5 py-2.5">Zones d'accès</th>
                    <th class="px-5 py-2.5 text-right">Disponibles</th>
                    <th class="px-5 py-2.5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody>
                  @for (r of g.rows; track r.modelId) {
                    <tr class="border-b border-line/60 hover:bg-bg transition-colors">
                      <td class="px-5 py-3 font-medium text-ink">{{ r.modelName }}</td>
                      <td class="px-5 py-3">
                        <span class="inline-flex gap-1 flex-wrap">
                          @for (z of r.accessZones; track z) {
                            <span class="text-[11px] font-semibold px-2 py-0.5 rounded-full text-white"
                                  [style.background]="zoneColor(z)">{{ z }}</span>
                          }
                        </span>
                      </td>
                      <td class="px-5 py-3 text-right text-2xl font-extrabold text-primary">{{ r.injectedCount | num }}</td>
                      <td class="px-5 py-3 text-right">
                        <button (click)="open(r)"
                                class="px-3 py-1.5 rounded-lg text-white text-sm font-medium transition-opacity hover:opacity-90"
                                style="background:var(--primary)">Gérer</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      }
    }
  `
})
export class BadgesComponent implements OnInit {
  loading = signal(true);
  error = signal(false);
  rows = signal<Availability[]>([]);
  selectedEvent: number | null = null;
  private filterEvent = signal<number | null>(null);

  eventOptions = computed(() => {
    const seen = new Map<number, string>();
    for (const r of this.rows()) if (!seen.has(r.eventId)) seen.set(r.eventId, r.eventTitle);
    return [...seen.entries()].map(([id, title]) => ({ id, title }));
  });

  groups = computed<EventGroup[]>(() => {
    const ev = this.filterEvent();
    const filtered = this.rows().filter(r => ev == null || r.eventId === ev);
    const map = new Map<number, EventGroup>();
    for (const r of filtered) {
      let g = map.get(r.eventId);
      if (!g) {
        g = { eventId: r.eventId, eventTitle: r.eventTitle, eventDate: r.eventDate, rows: [] };
        map.set(r.eventId, g);
      }
      g.rows.push(r);
    }
    return [...map.values()];
  });

  totalInjected = computed(() => this.rows().reduce((s, r) => s + r.injectedCount, 0));

  constructor(private badges: BadgeService, private router: Router) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.badges.availability().subscribe({
      // Categorie BADGE uniquement (Invitations et Acces ont leur propre page).
      // La categorie vient de l'API (fih.badge.model-categories).
      next: (r) => { this.rows.set(r.filter(x => x.category === 'BADGE')); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }

  onEventChange(): void { this.filterEvent.set(this.selectedEvent); }
  zoneColor(z: string): string { return zoneColor(z); }
  open(r: Availability): void { this.router.navigate(['badges', r.eventId, r.modelId]); }
}
