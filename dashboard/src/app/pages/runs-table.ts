import { DatePipe } from '@angular/common';
import { Component, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Run } from '../core/models';
import { StatusBadge } from './status-badge';

/** Runs in a table. Click a row to see its output. */
@Component({
  selector: 'sk-runs-table',
  imports: [DatePipe, RouterLink, StatusBadge],
  template: `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Run</th>
            @if (showJob()) {
              <th>Job</th>
            }
            <th>Status</th>
            <th>Trigger</th>
            <th>Attempt</th>
            <th>Scheduled</th>
            <th>Finished</th>
          </tr>
        </thead>
        <tbody>
          @for (run of runs(); track run.id) {
            <tr class="clickable" (click)="toggle(run.id)" [class.open]="openId() === run.id">
              <td>#{{ run.id }}</td>
              @if (showJob()) {
                <td>
                  <a [routerLink]="['/jobs', run.jobName]" (click)="$event.stopPropagation()">{{
                    run.jobName
                  }}</a>
                </td>
              }
              <td><sk-status [status]="run.status" /></td>
              <td>{{ run.trigger.toLowerCase() }}</td>
              <td>{{ run.attempt }}</td>
              <td>{{ run.scheduledFor | date: 'yyyy-MM-dd HH:mm:ss' }}</td>
              <td>{{ run.finishedAt ? (run.finishedAt | date: 'yyyy-MM-dd HH:mm:ss') : '–' }}</td>
            </tr>
            @if (openId() === run.id) {
              <tr class="output-row">
                <td [attr.colspan]="showJob() ? 7 : 6">
                  <pre>{{ run.output || 'No output yet.' }}</pre>
                </td>
              </tr>
            }
          } @empty {
            <tr>
              <td [attr.colspan]="showJob() ? 7 : 6" class="empty">{{ emptyText() }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .clickable {
      cursor: pointer;
    }
    .clickable:hover,
    .open {
      background: var(--hover);
    }
    pre {
      margin: 0;
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 13px;
    }
  `,
})
export class RunsTable {
  readonly runs = input.required<Run[]>();
  readonly showJob = input(true);
  readonly emptyText = input('No runs yet.');

  protected readonly openId = signal<number | null>(null);

  protected toggle(id: number): void {
    this.openId.update((open) => (open === id ? null : id));
  }
}
