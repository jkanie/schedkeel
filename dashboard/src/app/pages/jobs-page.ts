import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Job } from '../core/models';
import { describeError } from '../core/problem';
import { JobForm } from './job-form';
import { StatusBadge } from './status-badge';

@Component({
  selector: 'sk-jobs-page',
  imports: [RouterLink, StatusBadge, JobForm],
  template: `
    <div class="head">
      <h1>Jobs</h1>
      @if (auth.canChange() && !creating()) {
        <button type="button" (click)="creating.set(true)">New job</button>
      }
    </div>
    @if (creating()) {
      <sk-job-form (created)="onCreated($event)" (cancelled)="creating.set(false)" />
    }
    @if (notice(); as message) {
      <p class="notice" role="status">{{ message }}</p>
    }
    @if (error(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Schedule</th>
            <th>Type</th>
            <th>State</th>
            @if (auth.canChange()) {
              <th><span class="sr-only">Actions</span></th>
            }
          </tr>
        </thead>
        <tbody>
          @for (job of jobs(); track job.name) {
            <tr>
              <td>
                <a [routerLink]="['/jobs', job.name]">{{ job.name }}</a>
                @if (job.description) {
                  <div class="sub">{{ job.description }}</div>
                }
              </td>
              <td>
                <code>{{ job.cron }}</code>
                <div class="sub">{{ job.timeZone }}</div>
              </td>
              <td>{{ job.type }}</td>
              <td><sk-status [status]="job.enabled ? 'ENABLED' : 'PAUSED'" /></td>
              @if (auth.canChange()) {
                <td class="actions">
                  <button type="button" class="ghost" (click)="trigger(job)">Run now</button>
                  @if (job.enabled) {
                    <button type="button" class="ghost" (click)="pause(job)">Pause</button>
                  } @else {
                    <button type="button" class="ghost" (click)="resume(job)">Resume</button>
                  }
                </td>
              }
            </tr>
          } @empty {
            <tr>
              <td colspan="5" class="empty">
                @if (loaded()) {
                  No jobs yet.
                } @else {
                  Loading…
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }
    .actions {
      text-align: right;
      white-space: nowrap;
    }
    .actions button + button {
      margin-left: 6px;
    }
  `,
})
export class JobsPage {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly jobs = signal<Job[]>([]);
  protected readonly loaded = signal(false);
  protected readonly creating = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.api.jobs().subscribe({
      next: (jobs) => {
        this.jobs.set(jobs);
        this.loaded.set(true);
      },
      error: (e: unknown) => this.error.set(describeError(e)),
    });
  }

  protected onCreated(job: Job): void {
    this.creating.set(false);
    this.notice.set(`Created ${job.name}.`);
    this.load();
  }

  protected trigger(job: Job): void {
    this.act(this.api.trigger(job.name), (run) => `Queued run ${run.id} of ${job.name}.`);
  }

  protected pause(job: Job): void {
    this.act(this.api.pause(job.name), () => `Paused ${job.name}.`);
  }

  protected resume(job: Job): void {
    this.act(this.api.resume(job.name), () => `Resumed ${job.name}.`);
  }

  private act<T>(call: Observable<T>, message: (value: T) => string): void {
    this.error.set(null);
    call.subscribe({
      next: (value) => {
        this.notice.set(message(value));
        this.load();
      },
      error: (e: unknown) => this.error.set(describeError(e)),
    });
  }
}
