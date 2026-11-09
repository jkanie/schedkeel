import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Job, Revision, Run } from '../core/models';
import { describeError } from '../core/problem';
import { RunsTable } from './runs-table';
import { StatusBadge } from './status-badge';

@Component({
  selector: 'sk-job-page',
  imports: [DatePipe, RouterLink, RunsTable, StatusBadge],
  template: `
    <p><a routerLink="/jobs">← All jobs</a></p>
    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
    @if (job(); as job) {
      <div class="head">
        <h1>{{ job.name }}</h1>
        <sk-status [status]="job.enabled ? 'ENABLED' : 'PAUSED'" />
        @if (auth.canChange()) {
          <span class="spacer"></span>
          <button type="button" (click)="trigger()">Run now</button>
        }
      </div>
      @if (job.description) {
        <p class="sub">{{ job.description }}</p>
      }
      @if (notice(); as message) {
        <p class="notice" role="status">{{ message }}</p>
      }
      <dl class="card facts">
        <dt>Schedule</dt>
        <dd>
          <code>{{ job.cron }}</code> in {{ job.timeZone }}
        </dd>
        <dt>Type</dt>
        <dd>{{ job.type }}</dd>
        <dt>Target</dt>
        <dd class="mono">{{ job.target }}</dd>
        <dt>Overlap</dt>
        <dd>{{ job.concurrency === 'FORBID' ? 'Skip while a run is active' : 'Allow' }}</dd>
        <dt>Retries</dt>
        <dd>{{ job.maxRetries }} (first wait {{ job.retryBackoffSeconds }} s, then doubling)</dd>
        <dt>Timeout</dt>
        <dd>{{ job.timeoutSeconds }} s</dd>
        <dt>Last change</dt>
        <dd>
          {{ job.updatedAt | date: 'yyyy-MM-dd HH:mm:ss' }} by {{ job.updatedBy || 'system' }}
        </dd>
      </dl>

      <h2>Recent runs</h2>
      <sk-runs-table [runs]="runs()" [showJob]="false" />

      <h2>History</h2>
      <ol class="history">
        @for (rev of history(); track rev.revision) {
          <li>
            <strong>{{ rev.change.toLowerCase() }}</strong>
            by {{ rev.changedBy || 'system' }}, {{ rev.changedAt | date: 'yyyy-MM-dd HH:mm:ss' }}
            <span class="sub">
              <code>{{ rev.job.cron }}</code> · {{ rev.job.enabled ? 'enabled' : 'paused' }}
            </span>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .head {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .head h1 {
      margin: 0;
    }
    .spacer {
      flex: 1;
    }
    .facts {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 8px 24px;
      margin: 16px 0 0;
    }
    dt {
      color: var(--muted);
    }
    dd {
      margin: 0;
      overflow-wrap: anywhere;
    }
    .history {
      padding-left: 20px;
      display: grid;
      gap: 6px;
    }
    .history .sub {
      margin-left: 8px;
    }
  `,
})
export class JobPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  /** Bound from the :name route parameter. */
  readonly name = input.required<string>();

  protected readonly job = signal<Job | null>(null);
  protected readonly runs = signal<Run[]>([]);
  protected readonly history = signal<Revision[]>([]);
  protected readonly notice = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => this.load(this.name()));
  }

  private load(name: string): void {
    forkJoin({
      job: this.api.job(name),
      runs: this.api.runs({ job: name, size: 20 }),
      history: this.api.history(name),
    }).subscribe({
      next: ({ job, runs, history }) => {
        this.job.set(job);
        this.runs.set(runs.runs);
        this.history.set(history);
      },
      error: (e: unknown) => this.error.set(describeError(e)),
    });
  }

  protected trigger(): void {
    const name = this.name();
    this.api.trigger(name).subscribe({
      next: (run) => {
        this.notice.set(`Queued run ${run.id}.`);
        this.load(name);
      },
      error: (e: unknown) => this.error.set(describeError(e)),
    });
  }
}
