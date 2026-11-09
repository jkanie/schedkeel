import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, startWith, switchMap, timer } from 'rxjs';
import { ApiService } from '../core/api.service';
import { RUN_STATUSES, Run, RunStatus } from '../core/models';
import { describeError } from '../core/problem';
import { RunsTable } from './runs-table';

/** Refreshes on its own, so a queued run turns green without a reload. */
export const REFRESH_MS = 5000;

@Component({
  selector: 'sk-runs-page',
  imports: [FormsModule, RunsTable],
  template: `
    <div class="head">
      <h1>Runs</h1>
      <label class="inline">
        Status
        <select [ngModel]="status()" (ngModelChange)="setStatus($event)">
          <option value="">All</option>
          @for (s of statuses; track s) {
            <option [value]="s">{{ s.replace('_', ' ').toLowerCase() }}</option>
          }
        </select>
      </label>
      <span class="sub">{{ total() }} in total · refreshes every {{ refreshSeconds }} s</span>
    </div>
    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
    <sk-runs-table [runs]="runs()" emptyText="No runs match." />
  `,
  styles: `
    .head {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
    }
    .inline {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  `,
})
export class RunsPage {
  private readonly api = inject(ApiService);

  protected readonly statuses = RUN_STATUSES;
  protected readonly refreshSeconds = REFRESH_MS / 1000;
  protected readonly status = signal<RunStatus | ''>('');
  protected readonly runs = signal<Run[]>([]);
  protected readonly total = signal(0);
  protected readonly error = signal<string | null>(null);
  private readonly refresh = new Subject<void>();

  constructor() {
    this.refresh
      .pipe(
        startWith(undefined),
        switchMap(() => timer(0, REFRESH_MS)),
        switchMap(() => this.api.runs({ status: this.status(), size: 50 })),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe({
        next: (page) => {
          this.runs.set(page.runs);
          this.total.set(page.totalElements);
          this.error.set(null);
        },
        error: (e: unknown) => this.error.set(describeError(e)),
      });
  }

  protected setStatus(status: RunStatus | ''): void {
    this.status.set(status);
    this.refresh.next();
  }
}
