import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * Interrupteur on/off — un vrai toggle, pas une case HTML brute.
 * Piste + pastille glissante, cuivre à l'état actif (cohérent avec --accent).
 * Accessible : role="switch", pilotable au clavier.
 */
@Component({
  selector: 'app-toggle',
  standalone: true,
  template: `
    <button type="button" role="switch"
            [attr.aria-checked]="checked"
            [disabled]="disabled"
            (click)="onToggle()"
            class="relative inline-flex items-center h-6 w-11 rounded-full transition-colors duration-200 shrink-0 disabled:opacity-40 disabled:cursor-not-allowed"
            [style.background]="checked ? 'var(--accent)' : 'var(--line)'">
      <span class="inline-block h-5 w-5 rounded-full bg-white shadow transform transition-transform duration-200"
            [style.transform]="checked ? 'translateX(22px)' : 'translateX(2px)'"></span>
    </button>
  `
})
export class ToggleComponent {
  @Input() checked = false;
  @Input() disabled = false;
  @Output() checkedChange = new EventEmitter<boolean>();

  onToggle(): void {
    if (this.disabled) return;
    this.checked = !this.checked;
    this.checkedChange.emit(this.checked);
  }
}
