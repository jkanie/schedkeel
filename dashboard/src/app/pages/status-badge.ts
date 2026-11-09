import { Component, computed, input } from '@angular/core';
import { RunStatus } from '../core/models';

/** A coloured label for a run status, or for a job's enabled flag. */
@Component({
  selector: 'sk-status',
  template: `<span class="badge" [attr.data-tone]="tone()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.03em;
      background: var(--chip);
      color: var(--muted);
      white-space: nowrap;
    }
    [data-tone='good'] {
      background: var(--good-bg);
      color: var(--good);
    }
    [data-tone='bad'] {
      background: var(--bad-bg);
      color: var(--bad);
    }
    [data-tone='busy'] {
      background: var(--busy-bg);
      color: var(--busy);
    }
  `,
})
export class StatusBadge {
  readonly status = input.required<RunStatus | 'ENABLED' | 'PAUSED'>();

  protected readonly label = computed(() => this.status().replace('_', ' ').toLowerCase());
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case 'SUCCEEDED':
      case 'ENABLED':
        return 'good';
      case 'FAILED':
      case 'TIMED_OUT':
        return 'bad';
      case 'RUNNING':
      case 'QUEUED':
        return 'busy';
      default:
        return 'neutral';
    }
  });
}
