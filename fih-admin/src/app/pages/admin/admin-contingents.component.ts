import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/admin.service';
import { Contingent, ContingentLigne, EventOption, ModelOption, CreateContingentRequest } from '../../core/models';
import { LoadingSkeletonComponent } from '../../shared/loading-skeleton.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { AlertBannerComponent, AlertTone } from '../../shared/alert-banner.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { NumPipe, GDatePipe } from '../../shared/format';
import { humanizeError } from '../../shared/api-error';

type Pending =
  | { kind: 'revoke'; c: Contingent }
  | { kind: 'delete'; c: Contingent };

/**
 * Administration -> Lots d'invitations (contingents).
 *
 * Probleme 3 — refonte : en-tete + compteur, bandeau de disponibilite lisible,
 * barre d'avancement, dialogues styles (plus de window.confirm), erreurs
 * francaises via humanizeError. Le cas « 0 nom » est correctement formule.
 *
 * Rappel metier : seuls les types INVITATION font l'objet d'un lot. Pour un
 * badge / kit / acces, il n'y a pas de lot — il suffit d'activer le type dans
 * les droits de l'utilisateur (page Utilisateurs).
 */
@Component({
  selector: 'app-admin-contingents',
  standalone: true,
  imports: [
    FormsModule, LoadingSkeletonComponent, EmptyStateComponent,
    AlertBannerComponent, ConfirmDialogComponent, NumPipe, GDatePipe
  ],
  template: `
    <div class="flex items-center gap-3 mb-1">
      <span class="grid place-items-center w-10 h-10 rounded-xl shrink-0"
            style="background:rgba(176,102,60,.10);color:var(--accent)">
        <span class="msr text-[22px]">inventory_2</span>
      </span>
      <div>
        <h2 class="text-xl font-bold text-ink leading-tight">Lots d'invitations</h2>
        <p class="text-sm text-muted leading-tight">Affectez un volume d'invitations d'un type a un utilisateur.</p>
      </div>
      @if (!loading()) {
        <span class="ml-auto text-sm font-semibold px-3 py-1 rounded-full shrink-0"
              style="background:var(--bg);border:1px solid var(--line)">{{ lots().length }}</span>
      }
    </div>

    <div class="h-4"></div>

    @if (banner()) {
      <app-alert-banner [message]="banner()!" [tone]="bannerTone()" [dismissible]="true"
                        (dismiss)="banner.set(null)" />
    }

    <div class="surface-card p-5 mb-6">
      <div class="font-semibold text-ink mb-1 flex items-center gap-2">
        <span class="msr text-[18px]" style="color:var(--accent)">add_box</span> Nouveau lot
      </div>
      <p class="text-xs text-muted mb-3">
        Seules les invitations peuvent faire l'objet d'un lot. Pour un badge ou un acces,
        activez le type dans les droits de l'utilisateur : il verra alors la totalite.
      </p>
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <select [(ngModel)]="formEventId" (ngModelChange)="refreshDisponibilite()"
                class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent">
          <option [ngValue]="null">Evenement…</option>
          @for (e of events(); track e.reference) { <option [ngValue]="e.reference">{{ e.titre }}</option> }
        </select>
        <select [(ngModel)]="formModelId" (ngModelChange)="refreshDisponibilite()"
                class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent">
          <option [ngValue]="null">Type d'invitation…</option>
          @for (m of assignableOptions(); track m.modelId) { <option [ngValue]="m.modelId">{{ m.modelName }}</option> }
        </select>
        <select [(ngModel)]="formUserId"
                class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent">
          <option [ngValue]="null">Utilisateur…</option>
          @for (u of userOptions(); track u.id) { <option [ngValue]="u.id">{{ u.label }}</option> }
        </select>
      </div>

      <!-- Bascule du mode de selection -->
      <div class="flex items-center gap-2 mt-4 mb-3">
        <button (click)="setMode('auto')"
                class="px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors"
                [style.background]="mode() === 'auto' ? 'var(--primary)' : 'white'"
                [style.color]="mode() === 'auto' ? 'white' : 'var(--ink)'"
                [style.border-color]="mode() === 'auto' ? 'var(--primary)' : 'var(--line)'">
          Automatique
        </button>
        <button (click)="setMode('range')"
                class="px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors"
                [style.background]="mode() === 'range' ? 'var(--primary)' : 'white'"
                [style.color]="mode() === 'range' ? 'white' : 'var(--ink)'"
                [style.border-color]="mode() === 'range' ? 'var(--primary)' : 'var(--line)'">
          Plage manuelle
        </button>
        <span class="text-xs text-muted ml-1">
          {{ mode() === 'auto' ? 'Le systeme prend les premieres invitations libres.'
                               : 'Vous indiquez la plage exacte de numeros de serie.' }}
        </span>
      </div>

      @if (mode() === 'auto') {
        <div class="grid grid-cols-1 sm:grid-cols-4 gap-3">
          <input [(ngModel)]="formTaille" type="number" min="1" placeholder="Volume"
                 class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent" />
          <button (click)="create()" [disabled]="busy()"
                  class="px-4 py-2.5 rounded-xl text-white font-medium disabled:opacity-50 sm:col-start-4"
                  style="background:var(--primary)">Affecter</button>
        </div>
      } @else {
        <div class="grid grid-cols-1 sm:grid-cols-4 gap-3">
          <div>
            <label class="block text-xs text-muted mb-1">N° de serie debut</label>
            <input [(ngModel)]="formStart" (ngModelChange)="onRangeChange()" placeholder="6310000000"
                   class="w-full px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent font-mono" />
          </div>
          <div>
            <label class="block text-xs text-muted mb-1">Quantite (calcule la fin)</label>
            <input [(ngModel)]="formQty" (ngModelChange)="onRangeChange()" type="number" min="1" placeholder="15"
                   class="w-full px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent" />
          </div>
          <div>
            <label class="block text-xs text-muted mb-1">N° de serie fin</label>
            <input [(ngModel)]="formEnd" placeholder="auto ou manuel"
                   class="w-full px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent font-mono" />
          </div>
          <div class="flex items-end">
            <button (click)="create()" [disabled]="busy()"
                    class="w-full px-4 py-2.5 rounded-xl text-white font-medium disabled:opacity-50"
                    style="background:var(--primary)">Affecter</button>
          </div>
        </div>
        <p class="text-xs text-muted mt-2">
          Renseignez le debut, puis la quantite (la fin se calcule) ou directement la fin.
          Chaque numero est verifie : il doit exister, etre du bon type et de l'evenement choisi,
          et ne pas etre deja affecte ni deja dans un autre lot.
        </p>
      }

      @if (mode() === 'auto' && libres() !== null) {
        <div class="mt-3 flex items-center gap-2 text-sm">
          <span class="msr text-[18px]" [style.color]="libres()! > 0 ? 'var(--success)' : 'var(--warn)'">
            {{ libres()! > 0 ? 'check_circle' : 'error' }}
          </span>
          <span [style.color]="libres()! > 0 ? 'var(--ink)' : 'var(--warn)'">
            {{ libres() | num }} invitation(s) libre(s) pour ce couple evenement / type.
          </span>
        </div>
      }
    </div>

    @if (loading()) {
      <app-loading-skeleton [height]="300" />
    } @else if (error()) {
      <app-empty-state [error]="true" title="Impossible de charger les lots"
                       message="Le serveur est peut-etre indisponible. Reessayez dans un instant." />
    } @else if (lots().length === 0) {
      <app-empty-state icon="inventory_2" title="Aucun lot" message="Aucun lot n'a encore ete affecte." />
    } @else {
      @for (c of lots(); track c.id) {
        <div class="surface-card mb-4 overflow-hidden">
          <div class="px-5 py-4 flex flex-wrap items-center gap-4">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-semibold text-ink">{{ c.modelName }}</span>
                <span class="text-xs text-muted">{{ c.eventTitle }} · {{ c.eventDate | gdate }}</span>
                @if (c.actif) {
                  <span class="text-xs font-semibold px-2 py-0.5 rounded-full"
                        style="background:rgba(18,112,74,.10);color:var(--success)">Actif</span>
                } @else {
                  <span class="text-xs font-semibold px-2 py-0.5 rounded-full"
                        style="background:rgba(164,85,27,.10);color:var(--warn)">Revoque</span>
                }
              </div>
              <div class="text-xs text-muted mt-0.5">
                Affecte a <span class="text-ink font-medium">{{ c.displayName }}</span> ({{ c.username }})
              </div>
            </div>

            <div class="w-48 shrink-0">
              <div class="flex justify-between text-xs mb-1">
                <span class="text-muted">Avancement</span>
                <span class="font-semibold text-ink">{{ c.nommees | num }} / {{ c.taille | num }}</span>
              </div>
              <div class="h-2 rounded-full bg-line overflow-hidden">
                <div class="h-full rounded-full transition-all" style="background:var(--success)"
                     [style.width.%]="c.taille ? (c.nommees * 100 / c.taille) : 0"></div>
              </div>
            </div>

            <div class="flex items-center gap-2 shrink-0">
              <button (click)="toggleLines(c)"
                      class="px-3 py-1.5 rounded-lg text-xs font-medium border border-line hover:bg-bg">
                {{ expanded() === c.id ? 'Masquer' : 'Detail' }}
              </button>
              @if (c.actif) {
                <button (click)="startRevoke(c)" [disabled]="busy()"
                        class="px-3 py-1.5 rounded-lg text-xs font-medium border border-line hover:bg-bg disabled:opacity-50">
                  Revoquer
                </button>
              }
              <button (click)="startDelete(c)" [disabled]="busy() || c.nommees > 0"
                      [title]="c.nommees > 0 ? 'Des invitations sont deja nommees : revoquez plutot' : 'Supprimer'"
                      class="grid place-items-center w-8 h-8 rounded-lg border border-line hover:bg-bg disabled:opacity-30">
                <span class="msr text-[17px]" style="color:var(--warn)">delete</span>
              </button>
            </div>
          </div>

          @if (expanded() === c.id) {
            <div class="border-t border-line bg-bg px-5 py-4">
              <div class="text-sm font-medium text-ink mb-2">
                Lot #{{ c.id }} — cree le {{ c.createdAt | gdate }} par {{ c.createdBy || '—' }}
                @if (!c.actif) { · revoque par {{ c.revokedBy || '—' }} }
              </div>
              @if (linesLoading()) {
                <app-loading-skeleton [height]="120" />
              } @else if (lines().length === 0) {
                <app-empty-state icon="inbox" title="Lot vide" message="Ce lot ne contient aucune invitation." />
              } @else {
                <div class="surface-card overflow-hidden">
                  <div class="overflow-x-auto max-h-80">
                    <table class="w-full text-xs">
                      <thead>
                        <tr class="text-left text-muted border-b border-line">
                          <th class="px-3 py-2">N° de serie</th>
                          <th class="px-3 py-2">Code-barres</th>
                          <th class="px-3 py-2">Affecte a</th>
                          <th class="px-3 py-2">Nomme par</th>
                          <th class="px-3 py-2">Le</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (l of lines(); track l.numeroserie) {
                          <tr class="border-b border-line/50 hover:bg-bg">
                            <td class="px-3 py-1.5 font-mono text-ink">{{ l.numeroserie }}</td>
                            <td class="px-3 py-1.5 font-mono text-muted">{{ l.codebarre || '—' }}</td>
                            <td class="px-3 py-1.5">
                              @if (l.affecteeA) { <span class="text-ink">{{ l.affecteeA }}</span> }
                              @else { <span class="text-muted italic">en attente</span> }
                            </td>
                            <td class="px-3 py-1.5 text-muted">{{ l.updatedBy || '—' }}</td>
                            <td class="px-3 py-1.5 text-muted">{{ fmt(l.updatedAt) }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </div>
              }
            </div>
          }
        </div>
      }
    }

    <app-confirm-dialog [open]="pending()?.kind === 'revoke'"
                        title="Revoquer le lot"
                        [message]="revokeMessage()"
                        tone="danger" confirmLabel="Revoquer"
                        (confirm)="confirmRevoke()" (cancel)="pending.set(null)" />

    <app-confirm-dialog [open]="pending()?.kind === 'delete'"
                        title="Supprimer le lot"
                        [message]="deleteMessage()"
                        tone="danger" confirmLabel="Supprimer"
                        (confirm)="confirmDelete()" (cancel)="pending.set(null)" />
  `
})
export class AdminContingentsComponent implements OnInit {
  loading = signal(true);
  error = signal(false);
  busy = signal(false);
  lots = signal<Contingent[]>([]);
  lines = signal<ContingentLigne[]>([]);
  linesLoading = signal(false);
  expanded = signal<number | null>(null);
  events = signal<EventOption[]>([]);
  options = signal<ModelOption[]>([]);
  userOptions = signal<{ id: number; label: string }[]>([]);
  libres = signal<number | null>(null);
  banner = signal<string | null>(null);
  bannerTone = signal<AlertTone>('success');
  pending = signal<Pending | null>(null);

  formEventId: number | null = null;
  formModelId: number | null = null;
  formUserId: number | null = null;
  formTaille: number | null = null;

  // Mode de selection : auto (N premiers libres) ou plage manuelle.
  mode = signal<'auto' | 'range'>('auto');
  formStart = '';
  formEnd = '';
  formQty: number | null = null;

  constructor(private api: AdminService) {}

  ngOnInit(): void { this.load(); }

  /** Seuls les vrais types « Invitation » (et non payants) peuvent avoir un lot. */
  assignableOptions(): ModelOption[] {
    return this.options().filter(o => o.invitation && !o.paid);
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api.events().subscribe({ next: (e) => this.events.set(e), error: () => this.events.set([]) });
    this.api.modelOptions().subscribe({ next: (o) => this.options.set(o), error: () => this.options.set([]) });
    this.api.users().subscribe({
      next: (u) => this.userOptions.set(
        u.filter(x => x.enabled).map(x => ({ id: x.id, label: x.displayName + ' (' + x.username + ')' }))),
      error: () => this.userOptions.set([])
    });
    this.api.contingents().subscribe({
      next: (c) => { this.lots.set(c); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }

  setMode(m: 'auto' | 'range'): void { this.mode.set(m); }

  /** Auto-calcule le numero de fin = debut + (quantite - 1), sur 10 chiffres. */
  onRangeChange(): void {
    const start = (this.formStart || '').trim();
    if (!/^\d{1,10}$/.test(start) || !this.formQty || this.formQty < 1) return;
    const end = (BigInt(start) + BigInt(this.formQty - 1)).toString();
    this.formEnd = end.padStart(start.length >= 10 ? start.length : 10, '0');
  }

  refreshDisponibilite(): void {
    this.libres.set(null);
    if (this.formEventId == null || this.formModelId == null) return;
    this.api.disponibilite(this.formEventId, this.formModelId).subscribe({
      next: (d) => this.libres.set(d.libres),
      error: () => this.libres.set(null)
    });
  }

  create(): void {
    if (this.formEventId == null || this.formModelId == null || this.formUserId == null) {
      this.fail('Evenement, type et utilisateur sont obligatoires.');
      return;
    }

    const req: CreateContingentRequest = {
      eventId: this.formEventId, modelId: this.formModelId, userId: this.formUserId
    };

    if (this.mode() === 'range') {
      const start = (this.formStart || '').trim();
      if (!/^\d{1,10}$/.test(start)) {
        this.fail('Le numero de serie de debut est obligatoire (jusqu a 10 chiffres).');
        return;
      }
      const end = (this.formEnd || '').trim();
      if (!end && (!this.formQty || this.formQty < 1)) {
        this.fail('Indiquez un numero de fin, ou une quantite pour le calculer.');
        return;
      }
      req.startSerie = start;
      if (end) req.endSerie = end;
      if (this.formQty) req.taille = this.formQty;
    } else {
      if (!this.formTaille || this.formTaille < 1) {
        this.fail('Le volume est obligatoire en mode automatique.');
        return;
      }
      req.taille = this.formTaille;
    }

    this.busy.set(true);
    this.api.createContingent(req).subscribe({
      next: () => {
        this.busy.set(false);
        this.formTaille = null; this.formStart = ''; this.formEnd = ''; this.formQty = null;
        this.ok('Lot affecte.');
        this.refreshDisponibilite();
        this.load();
      },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Affectation impossible.')); }
    });
  }

  toggleLines(c: Contingent): void {
    if (this.expanded() === c.id) { this.expanded.set(null); return; }
    this.expanded.set(c.id);
    this.lines.set([]);
    this.linesLoading.set(true);
    this.api.contingentLignes(c.id).subscribe({
      next: (l) => { this.lines.set(l); this.linesLoading.set(false); },
      error: (e) => { this.linesLoading.set(false); this.fail(humanizeError(e, 'Impossible de charger le detail.')); }
    });
  }

  startRevoke(c: Contingent): void { this.pending.set({ kind: 'revoke', c }); }
  startDelete(c: Contingent): void { this.pending.set({ kind: 'delete', c }); }

  revokeMessage(): string {
    const p = this.pending();
    if (p?.kind !== 'revoke') return '';
    const c = p.c;
    const noms = c.nommees === 0
      ? 'Aucun nom n a encore ete enregistre.'
      : c.nommees === 1
        ? 'Le nom deja enregistre est conserve.'
        : 'Les ' + c.nommees + ' noms deja enregistres sont conserves.';
    return c.username + ' ne verra plus ses invitations. ' + noms;
  }
  deleteMessage(): string {
    const p = this.pending();
    return p?.kind === 'delete'
      ? 'Le lot #' + p.c.id + ' sera supprime et ses invitations redeviendront libres.'
      : '';
  }

  confirmRevoke(): void {
    const p = this.pending();
    if (p?.kind !== 'revoke') return;
    const id = p.c.id;
    this.pending.set(null);
    this.busy.set(true);
    this.api.revoke(id).subscribe({
      next: () => { this.busy.set(false); this.ok('Lot revoque.'); this.load(); },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Revocation impossible.')); }
    });
  }

  confirmDelete(): void {
    const p = this.pending();
    if (p?.kind !== 'delete') return;
    const id = p.c.id;
    this.pending.set(null);
    this.busy.set(true);
    this.api.deleteContingent(id).subscribe({
      next: () => { this.busy.set(false); this.ok('Lot supprime.'); this.load(); },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Suppression impossible.')); }
    });
  }

  fmt(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? '—' : d.toLocaleString('fr-FR');
  }

  private ok(m: string): void { this.banner.set(m); this.bannerTone.set('success'); }
  private fail(m: string): void { this.banner.set(m); this.bannerTone.set('error'); }
}