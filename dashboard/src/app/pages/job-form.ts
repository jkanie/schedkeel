import { Component, inject, output, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ApiService } from '../core/api.service';
import { Job, JobRequest, JobType } from '../core/models';
import { describeError } from '../core/problem';

/** The server checks the cron fully; this catches the common 5-field mistake early. */
export function sixFieldCron(control: AbstractControl<string>): ValidationErrors | null {
  const fields = control.value.trim().split(/\s+/).filter(Boolean);
  return fields.length === 6 ? null : { sixFieldCron: { fields: fields.length } };
}

const TARGET_HINT: Record<JobType, string> = {
  LOG: 'Text to log when no payload is set',
  HTTP: 'URL to call, for example https://example.org/hook',
  KAFKA: 'Topic to publish to',
  AMQP: 'exchange/routing-key, for example billing/invoice.due',
  PULSAR: 'Topic, for example orders or persistent://tenant/ns/orders',
  BATCH: 'Spring Batch job name, for example archiveRuns',
};

@Component({
  selector: 'sk-job-form',
  imports: [ReactiveFormsModule],
  template: `
    <form class="card grid" [formGroup]="form" (ngSubmit)="submit()">
      <h2>New job</h2>
      <label>
        Name
        <input formControlName="name" placeholder="nightly-report" />
        @if (form.controls.name.touched && form.controls.name.hasError('pattern')) {
          <small class="error">Use lower-case letters, digits and hyphens.</small>
        }
      </label>
      <label>
        Cron (6 fields)
        <input formControlName="cron" class="mono" placeholder="0 0 2 * * *" />
        @if (form.controls.cron.touched && form.controls.cron.hasError('sixFieldCron')) {
          <small class="error">
            Use 6 fields: second, minute, hour, day of month, month, day of week.
          </small>
        }
      </label>
      <label>
        Time zone
        <input formControlName="timeZone" placeholder="UTC" />
      </label>
      <label>
        Type
        <select formControlName="type">
          <option>LOG</option>
          <option>HTTP</option>
          <option>KAFKA</option>
          <option>AMQP</option>
          <option>PULSAR</option>
          <option>BATCH</option>
        </select>
      </label>
      <label class="wide">
        Target
        <input formControlName="target" [placeholder]="targetHint()" />
      </label>
      <label class="wide">
        Payload (optional)
        <textarea formControlName="payload" rows="2"></textarea>
      </label>
      <label>
        Retries
        <input formControlName="maxRetries" type="number" min="0" max="10" />
      </label>
      <label>
        Timeout (seconds)
        <input formControlName="timeoutSeconds" type="number" min="1" />
      </label>
      @if (error(); as message) {
        <p class="error wide" role="alert">{{ message }}</p>
      }
      <div class="wide actions">
        <button type="submit" [disabled]="form.invalid || busy()">Create job</button>
        <button type="button" class="ghost" (click)="cancelled.emit()">Cancel</button>
      </div>
    </form>
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      align-items: start;
      gap: 12px 16px;
    }
    h2,
    .wide {
      grid-column: 1 / -1;
    }
    h2 {
      margin: 0;
    }
    .actions {
      display: flex;
      gap: 8px;
    }
  `,
})
export class JobForm {
  private readonly api = inject(ApiService);

  readonly created = output<Job>();
  readonly cancelled = output<void>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9-]*$/)]],
    cron: ['', [Validators.required, sixFieldCron]],
    timeZone: ['UTC'],
    type: ['LOG' as JobType, Validators.required],
    target: ['', Validators.required],
    payload: [''],
    maxRetries: [0, [Validators.min(0), Validators.max(10)]],
    timeoutSeconds: [300, [Validators.min(1)]],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly targetHint = signal(TARGET_HINT.LOG);

  constructor() {
    this.form.controls.type.valueChanges.subscribe((type) =>
      this.targetHint.set(TARGET_HINT[type]),
    );
  }

  protected submit(): void {
    const value = this.form.getRawValue();
    const request: JobRequest = {
      name: value.name,
      cron: value.cron.trim(),
      timeZone: value.timeZone.trim() || 'UTC',
      type: value.type,
      target: value.target.trim(),
      maxRetries: value.maxRetries,
      timeoutSeconds: value.timeoutSeconds,
      ...(value.payload ? { payload: value.payload } : {}),
    };
    this.busy.set(true);
    this.error.set(null);
    this.api.createJob(request).subscribe({
      next: (job) => {
        this.busy.set(false);
        this.created.emit(job);
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(describeError(e));
      },
    });
  }
}
