import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Modale de confirmation, stylée selon le design system. Remplace les
 * `window.confirm()` et `window.prompt()` du navigateur (« localhost:4200
 * indique… »), qui juraient avec le reste du backoffice.
 *
 * Deux modes :
 *  - confirmation simple (OK / Annuler) ;
 *  - saisie protégée (`promptLabel` renseigné), pour un mot de passe par
 *    exemple, sans passer par la boîte native.
 *
 * L'appelant contrôle l'affichage via `open`, et reçoit `confirm` (avec la
 * valeur saisie le cas échéant) ou `cancel`.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (open) {
      <div class="fixed inset-0 z-50 grid place-items-center p-4"
           style="background:rgba(26,28,30,.45)"
           (click)="onBackdrop($event)">
        <div class="surface-card w-full max-w-md p-6" style="box-shadow:0 20px 60px rgba(26,28,30,.30)"
             (click)="$event.stopPropagation()">
          <div class="flex items-start gap-3 mb-4">
            <span class="grid place-items-center w-10 h-10 rounded-xl shrink-0"
                  [style.background]="tone === 'danger' ? 'rgba(164,85,27,.10)' : 'rgba(176,102,60,.10)'"
                  [style.color]="tone === 'danger' ? 'var(--warn)' : 'var(--accent)'">
              <span class="msr text-[22px]">{{ tone === 'danger' ? 'warning' : 'help' }}</span>
            </span>
            <div class="min-w-0">
              <h3 class="text-lg font-bold text-ink leading-tight">{{ title }}</h3>
              @if (message) { <p class="text-sm text-muted mt-1 leading-snug whitespace-pre-line">{{ message }}</p> }
            </div>
          </div>

          @if (promptLabel) {
            <label class="block text-sm font-medium text-ink mb-1.5">{{ promptLabel }}</label>
            <input [type]="promptType" [(ngModel)]="promptValue" [placeholder]="promptPlaceholder"
                   (keydown.enter)="confirmNow()"
                   class="w-full px-3 py-2.5 rounded-xl border border-line bg-white outline-none focus:border-accent mb-1"
                   autofocus />
            @if (promptHint) { <p class="text-xs text-muted mb-2">{{ promptHint }}</p> }
          }

          <div class="flex justify-end gap-2 mt-5">
            <button type="button" (click)="cancel.emit()"
                    class="px-4 py-2 rounded-xl text-sm font-medium border border-line text-ink hover:bg-bg transition-colors">
              {{ cancelLabel }}
            </button>
            <button type="button" (click)="confirmNow()" [disabled]="!canConfirm()"
                    class="px-4 py-2 rounded-xl text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-40"
                    [style.background]="tone === 'danger' ? 'var(--warn)' : 'var(--primary)'">
              {{ confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ConfirmDialogComponent {
  @Input() open = false;
  @Input() title = 'Confirmer';
  @Input() message = '';
  @Input() tone: 'default' | 'danger' = 'default';
  @Input() confirmLabel = 'Confirmer';
  @Input() cancelLabel = 'Annuler';

  /** Renseigné = mode saisie. Sinon confirmation simple. */
  @Input() promptLabel = '';
  @Input() promptType: 'text' | 'password' = 'text';
  @Input() promptPlaceholder = '';
  @Input() promptHint = '';
  @Input() promptMinLength = 0;

  @Output() confirm = new EventEmitter<string>();
  @Output() cancel = new EventEmitter<void>();

  promptValue = '';

  canConfirm(): boolean {
    if (!this.promptLabel) return true;
    return this.promptValue.trim().length >= this.promptMinLength;
  }

  confirmNow(): void {
    if (!this.canConfirm()) return;
    const v = this.promptValue;
    this.promptValue = '';
    this.confirm.emit(v);
  }

  onBackdrop(_: MouseEvent): void {
    this.cancel.emit();
  }
}
