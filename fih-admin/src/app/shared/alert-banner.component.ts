import { Component, EventEmitter, Input, Output } from '@angular/core';

export type AlertTone = 'success' | 'error' | 'info';

/**
 * Bandeau de message inline, stylé selon les tokens du design system.
 * Remplace les chaînes brutes renvoyées par l'API : plus aucun code HTTP nu,
 * plus aucun nom de table à l'écran.
 */
@Component({
  selector: 'app-alert-banner',
  standalone: true,
  template: `
    <div class="surface-card flex items-start gap-3 px-4 py-3 mb-5"
         [style.border-color]="border()"
         [style.background]="bg()">
      <span class="msr text-[20px] mt-px shrink-0" [style.color]="fg()">{{ icon() }}</span>
      <div class="text-sm font-medium leading-snug flex-1 min-w-0" [style.color]="fg()">{{ message }}</div>
      @if (dismissible) {
        <button type="button" (click)="dismiss.emit()"
                class="msr text-[18px] shrink-0 text-muted hover:text-ink transition-colors"
                aria-label="Fermer">close</button>
      }
    </div>
  `
})
export class AlertBannerComponent {
  @Input({ required: true }) message = '';
  @Input() tone: AlertTone = 'info';
  @Input() dismissible = false;
  @Output() dismiss = new EventEmitter<void>();

  icon(): string {
    return this.tone === 'success' ? 'check_circle'
      : this.tone === 'error' ? 'error' : 'info';
  }
  fg(): string {
    return this.tone === 'success' ? 'var(--success)'
      : this.tone === 'error' ? 'var(--warn)' : 'var(--muted)';
  }
  bg(): string {
    return this.tone === 'success' ? 'rgba(18,112,74,.06)'
      : this.tone === 'error' ? 'rgba(164,85,27,.07)' : 'var(--bg)';
  }
  border(): string {
    return this.tone === 'success' ? 'rgba(18,112,74,.25)'
      : this.tone === 'error' ? 'rgba(164,85,27,.30)' : 'var(--line)';
  }
}
