import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../core/admin.service';
import { AppUser, ModelOption } from '../../core/models';
import { LoadingSkeletonComponent } from '../../shared/loading-skeleton.component';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { AlertBannerComponent, AlertTone } from '../../shared/alert-banner.component';
import { ToggleComponent } from '../../shared/toggle.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { humanizeError } from '../../shared/api-error';

type PendingAction =
  | { kind: 'delete'; user: AppUser }
  | { kind: 'password'; user: AppUser };

/**
 * Administration -> Utilisateurs.
 *
 * Probleme 3 — refonte : en-tete clair + compteur, interrupteurs (vrai toggle)
 * groupes par section Invitations / Badges / Acces avec compteur « n/total »
 * par section, dialogues styles (plus de window.confirm / window.prompt), et
 * messages d'erreur francais via humanizeError (plus de code HTTP nu ni de nom
 * de table a l'ecran).
 *
 * Rappel metier (Probleme 2) : cocher un type INVITATION ne suffit pas a le
 * rendre visible — il faut aussi affecter un lot. Pour tout autre type,
 * cocher = voir la totalite. L'astuce sous la section Invitations le dit.
 */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    FormsModule, LoadingSkeletonComponent, EmptyStateComponent,
    AlertBannerComponent, ToggleComponent, ConfirmDialogComponent
  ],
  template: `
    <div class="flex items-center gap-3 mb-1">
      <span class="grid place-items-center w-10 h-10 rounded-xl shrink-0"
            style="background:rgba(176,102,60,.10);color:var(--accent)">
        <span class="msr text-[22px]">manage_accounts</span>
      </span>
      <div>
        <h2 class="text-xl font-bold text-ink leading-tight">Utilisateurs</h2>
        <p class="text-sm text-muted leading-tight">Comptes du backoffice et types que chacun peut voir.</p>
      </div>
      @if (!loading()) {
        <span class="ml-auto text-sm font-semibold px-3 py-1 rounded-full shrink-0"
              style="background:var(--bg);border:1px solid var(--line)">{{ users().length }}</span>
      }
    </div>

    <div class="h-4"></div>

    @if (banner()) {
      <app-alert-banner [message]="banner()!" [tone]="bannerTone()" [dismissible]="true"
                        (dismiss)="banner.set(null)" />
    }

    <div class="surface-card p-5 mb-6">
      <div class="font-semibold text-ink mb-3 flex items-center gap-2">
        <span class="msr text-[18px]" style="color:var(--accent)">person_add</span> Nouveau compte
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-4 gap-3">
        <input [(ngModel)]="newUsername" placeholder="Nom d'utilisateur"
               class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent" />
        <input [(ngModel)]="newDisplayName" placeholder="Nom affiche"
               class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent" />
        <input [(ngModel)]="newPassword" type="password" placeholder="Mot de passe (8 car. min.)"
               class="px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent" />
        <button (click)="create()" [disabled]="busy()"
                class="px-4 py-2.5 rounded-xl text-white font-medium disabled:opacity-50"
                style="background:var(--primary)">Creer</button>
      </div>
      <p class="text-xs text-muted mt-2">
        Mot de passe hache (BCrypt). Un compte neuf ne voit rien tant qu'aucun type ni aucun lot ne lui est attribue.
      </p>
    </div>

    @if (loading()) {
      <app-loading-skeleton [height]="300" />
    } @else if (error()) {
      <app-empty-state [error]="true" title="Impossible de charger les comptes"
                       message="Le serveur est peut-etre indisponible. Reessayez dans un instant." />
    } @else if (users().length === 0) {
      <app-empty-state icon="group" title="Aucun compte" message="Creez un premier compte ci-dessus." />
    } @else {
      @for (u of users(); track u.id) {
        <div class="surface-card mb-4 overflow-hidden">
          <div class="px-5 py-3.5 border-b border-line flex flex-wrap items-center gap-3">
            <span class="grid place-items-center w-9 h-9 rounded-full text-sm font-bold shrink-0"
                  style="background:var(--bg);border:1px solid var(--line);color:var(--ink)">
              {{ initials(u.displayName) }}
            </span>
            <div class="min-w-0">
              <div class="font-semibold text-ink leading-tight">{{ u.displayName }}</div>
              <div class="text-xs text-muted">{{ u.username }} · cree par {{ u.createdBy || '—' }}</div>
            </div>

            <span class="text-xs font-semibold px-2 py-0.5 rounded-full"
                  [style.background]="u.enabled ? 'rgba(18,112,74,.10)' : 'rgba(164,85,27,.10)'"
                  [style.color]="u.enabled ? 'var(--success)' : 'var(--warn)'">
              {{ u.enabled ? 'Actif' : 'Desactive' }}
            </span>
            @if (u.contingentsActifs > 0) {
              <span class="text-xs px-2 py-0.5 rounded-full"
                    style="background:var(--bg);border:1px solid var(--line);color:var(--muted)">
                {{ u.contingentsActifs }} lot(s)
              </span>
            }

            <div class="flex-1"></div>

            <div class="flex items-center gap-2 shrink-0">
              <app-toggle [checked]="u.enabled" [disabled]="busy()" (checkedChange)="setEnabled(u, $event)" />
              <button (click)="startPassword(u)" [disabled]="busy()"
                      class="ml-1 grid place-items-center w-9 h-9 rounded-lg border border-line hover:bg-bg disabled:opacity-50"
                      title="Reinitialiser le mot de passe">
                <span class="msr text-[18px] text-muted">key</span>
              </button>
              <button (click)="startDelete(u)" [disabled]="busy() || u.contingentsActifs > 0"
                      [title]="u.contingentsActifs > 0 ? 'Revoquez d abord ses lots' : 'Supprimer'"
                      class="grid place-items-center w-9 h-9 rounded-lg border border-line hover:bg-bg disabled:opacity-30">
                <span class="msr text-[18px]" style="color:var(--warn)">delete</span>
              </button>
            </div>
          </div>

          <div class="px-5 py-4">
            <div class="flex items-center justify-between mb-4">
              <div class="text-sm font-medium text-ink">
                Types visibles
                <span class="text-muted font-normal ml-1">{{ selectionOf(u.id).size }}/{{ options().length }} actives</span>
              </div>
              <div class="flex gap-2">
                <button (click)="selectAll(u.id)" class="text-xs px-2.5 py-1 rounded-lg border border-line hover:bg-bg">Tout</button>
                <button (click)="selectNone(u.id)" class="text-xs px-2.5 py-1 rounded-lg border border-line hover:bg-bg">Aucun</button>
                <button (click)="savePermissions(u)" [disabled]="busy()"
                        class="text-xs px-3 py-1 rounded-lg text-white font-medium disabled:opacity-50"
                        style="background:var(--primary)">Enregistrer</button>
              </div>
            </div>

            @for (cat of categories; track cat.key) {
              @if (optionsOf(cat.key).length > 0) {
                <div class="mb-4 last:mb-0">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="msr text-[16px]" [style.color]="cat.color">{{ cat.icon }}</span>
                    <span class="text-xs font-semibold uppercase tracking-wide text-ink">{{ cat.label }}</span>
                    <span class="text-xs text-muted">{{ countIn(u.id, cat.key) }}/{{ optionsOf(cat.key).length }}</span>
                    <button (click)="toggleCat(u.id, cat.key)"
                            class="ml-auto text-[11px] px-2 py-0.5 rounded-md border border-line hover:bg-bg text-muted">
                      {{ allIn(u.id, cat.key) ? 'Decocher la section' : 'Cocher la section' }}
                    </button>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1">
                    @for (m of optionsOf(cat.key); track m.modelId) {
                      <div class="flex items-center gap-3 py-1.5">
                        <app-toggle [checked]="isChecked(u.id, m.modelId)"
                                    (checkedChange)="toggleModel(u.id, m.modelId)" />
                        <span class="text-sm text-ink flex-1 min-w-0 truncate">{{ m.modelName }}</span>
                        @if (m.invitation) {
                          <span class="text-[10px] font-semibold px-1.5 py-0.5 rounded"
                                style="background:rgba(176,102,60,.10);color:var(--accent)"
                                title="Visibilite par lot : cocher ne suffit pas, il faut affecter un lot">lot</span>
                        }
                        @if (m.paid) {
                          <span class="text-[10px] font-semibold" style="color:var(--warn)">payant</span>
                        }
                      </div>
                    }
                  </div>
                  @if (cat.key === 'INVITATION') {
                    <p class="text-[11px] text-muted mt-1.5 pl-1">
                      Les invitations se distribuent par lot (page « Lots d'invitations ») : cocher le type ne suffit pas.
                    </p>
                  }
                </div>
              }
            }
          </div>
        </div>
      }
    }

    <app-confirm-dialog [open]="pending()?.kind === 'delete'"
                        title="Supprimer le compte"
                        [message]="deleteMessage()"
                        tone="danger" confirmLabel="Supprimer"
                        (confirm)="confirmDelete()" (cancel)="pending.set(null)" />

    <app-confirm-dialog [open]="pending()?.kind === 'password'"
                        title="Reinitialiser le mot de passe"
                        [message]="passwordMessage()"
                        promptLabel="Nouveau mot de passe" promptType="password"
                        promptPlaceholder="8 caracteres minimum" [promptMinLength]="8"
                        promptHint="Le compte utilisera ce mot de passe a sa prochaine connexion."
                        confirmLabel="Reinitialiser"
                        (confirm)="confirmPassword($event)" (cancel)="pending.set(null)" />
  `
})
export class AdminUsersComponent implements OnInit {
  readonly categories = [
    { key: 'INVITATION', label: 'Invitations', icon: 'mail', color: 'var(--accent)' },
    { key: 'BADGE', label: 'Badges', icon: 'badge', color: 'var(--success)' },
    { key: 'ACCES', label: 'Portes / Acces', icon: 'sensor_door', color: 'var(--warn)' }
  ];

  loading = signal(true);
  error = signal(false);
  busy = signal(false);
  users = signal<AppUser[]>([]);
  options = signal<ModelOption[]>([]);
  banner = signal<string | null>(null);
  bannerTone = signal<AlertTone>('success');
  pending = signal<PendingAction | null>(null);

  private selection = signal<Map<number, Set<number>>>(new Map());

  newUsername = '';
  newDisplayName = '';
  newPassword = '';

  constructor(private api: AdminService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api.modelOptions().subscribe({
      next: (o) => this.options.set(o),
      error: () => this.error.set(true)
    });
    this.api.users().subscribe({
      next: (u) => {
        this.users.set(u);
        const map = new Map<number, Set<number>>();
        for (const user of u) map.set(user.id, new Set(user.modelIds));
        this.selection.set(map);
        this.loading.set(false);
      },
      error: () => { this.error.set(true); this.loading.set(false); }
    });
  }

  optionsOf(cat: string): ModelOption[] { return this.options().filter(o => o.category === cat); }
  selectionOf(id: number): Set<number> { return this.selection().get(id) ?? new Set<number>(); }
  isChecked(id: number, m: number): boolean { return this.selectionOf(id).has(m); }
  countIn(id: number, cat: string): number {
    const set = this.selectionOf(id);
    return this.optionsOf(cat).filter(o => set.has(o.modelId)).length;
  }
  allIn(id: number, cat: string): boolean {
    const opts = this.optionsOf(cat);
    return opts.length > 0 && this.countIn(id, cat) === opts.length;
  }

  toggleModel(id: number, m: number): void {
    const map = new Map(this.selection());
    const set = new Set(map.get(id) ?? []);
    if (set.has(m)) { set.delete(m); } else { set.add(m); }
    map.set(id, set);
    this.selection.set(map);
  }
  toggleCat(id: number, cat: string): void {
    const map = new Map(this.selection());
    const set = new Set(map.get(id) ?? []);
    const opts = this.optionsOf(cat);
    const all = opts.every(o => set.has(o.modelId));
    for (const o of opts) { if (all) { set.delete(o.modelId); } else { set.add(o.modelId); } }
    map.set(id, set);
    this.selection.set(map);
  }
  selectAll(id: number): void {
    const map = new Map(this.selection());
    map.set(id, new Set(this.options().map(o => o.modelId)));
    this.selection.set(map);
  }
  selectNone(id: number): void {
    const map = new Map(this.selection());
    map.set(id, new Set());
    this.selection.set(map);
  }

  savePermissions(u: AppUser): void {
    this.busy.set(true);
    this.api.setModels(u.id, [...this.selectionOf(u.id)]).subscribe({
      next: () => { this.busy.set(false); this.ok('Droits enregistres pour ' + u.displayName + '.'); this.load(); },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Enregistrement impossible.')); }
    });
  }

  create(): void {
    if (!this.newUsername.trim() || !this.newDisplayName.trim() || this.newPassword.length < 8) {
      this.fail("Nom d'utilisateur, nom affiche et mot de passe (8 caracteres minimum) sont obligatoires.");
      return;
    }
    this.busy.set(true);
    this.api.createUser({
      username: this.newUsername.trim(), displayName: this.newDisplayName.trim(), password: this.newPassword
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.newUsername = ''; this.newDisplayName = ''; this.newPassword = '';
        this.ok('Compte cree.'); this.load();
      },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Creation impossible.')); }
    });
  }

  setEnabled(u: AppUser, enabled: boolean): void {
    this.busy.set(true);
    this.api.updateUser(u.id, { displayName: u.displayName, enabled }).subscribe({
      next: () => { this.busy.set(false); this.ok(enabled ? 'Compte active.' : 'Compte desactive.'); this.load(); },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Mise a jour impossible.')); this.load(); }
    });
  }

  startPassword(u: AppUser): void { this.pending.set({ kind: 'password', user: u }); }
  startDelete(u: AppUser): void { this.pending.set({ kind: 'delete', user: u }); }

  passwordMessage(): string {
    const p = this.pending();
    return p?.kind === 'password' ? 'Compte « ' + p.user.displayName + ' » (' + p.user.username + ').' : '';
  }
  deleteMessage(): string {
    const p = this.pending();
    return p?.kind === 'delete'
      ? 'Le compte « ' + p.user.displayName + ' » sera definitivement supprime. Cette action est irreversible.'
      : '';
  }

  confirmPassword(password: string): void {
    const p = this.pending();
    if (p?.kind !== 'password') return;
    const id = p.user.id;
    this.pending.set(null);
    this.busy.set(true);
    this.api.resetPassword(id, { password }).subscribe({
      next: () => { this.busy.set(false); this.ok('Mot de passe reinitialise.'); },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Reinitialisation impossible.')); }
    });
  }

  confirmDelete(): void {
    const p = this.pending();
    if (p?.kind !== 'delete') return;
    const id = p.user.id;
    this.pending.set(null);
    this.busy.set(true);
    this.api.deleteUser(id).subscribe({
      next: () => { this.busy.set(false); this.ok('Compte supprime.'); this.load(); },
      error: (e) => { this.busy.set(false); this.fail(humanizeError(e, 'Suppression impossible.')); }
    });
  }

  initials(name: string): string {
    return (name || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join('') || '?';
  }

  private ok(m: string): void { this.banner.set(m); this.bannerTone.set('success'); }
  private fail(m: string): void { this.banner.set(m); this.bannerTone.set('error'); }
}
