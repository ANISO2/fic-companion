import { Component, Input } from '@angular/core';

/**
 * En-tête de section : icône dans une pastille cuivre, titre, et compteur.
 * Donne à chaque page (Invitations / Badges / Accès) une identité claire au
 * lieu d'une liste brute qui s'enchaîne.
 */
@Component({
  selector: 'app-section-header',
  standalone: true,
  template: `
    <div class="flex items-center gap-3 mb-5">
      <span class="grid place-items-center w-10 h-10 rounded-xl shrink-0"
            style="background:rgba(176,102,60,.10);color:var(--accent)">
        <span class="msr text-[22px]">{{ icon }}</span>
      </span>
      <div class="min-w-0">
        <h2 class="text-xl font-bold text-ink leading-tight">{{ title }}</h2>
        @if (subtitle) { <p class="text-sm text-muted leading-tight">{{ subtitle }}</p> }
      </div>
      @if (count !== null) {
        <span class="ml-auto text-sm font-semibold px-3 py-1 rounded-full shrink-0"
              style="background:var(--bg);border:1px solid var(--line);color:var(--ink)">
          {{ count }}{{ countSuffix }}
        </span>
      }
    </div>
  `
})
export class SectionHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() subtitle = '';
  @Input() icon = 'category';
  @Input() count: number | string | null = null;
  @Input() countSuffix = '';
}
