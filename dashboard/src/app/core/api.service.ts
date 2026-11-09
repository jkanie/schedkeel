import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Job, JobRequest, Revision, Run, RunPage, RunStatus } from './models';

interface Embedded<K extends string, T> {
  _embedded?: Record<K, T[]>;
  page?: { totalElements: number };
}

/** Typed calls to the schedkeel API. Unwraps HAL collections. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  jobs(): Observable<Job[]> {
    return this.http
      .get<Embedded<'jobs', Job>>('/api/jobs')
      .pipe(map((body) => body._embedded?.jobs ?? []));
  }

  job(name: string): Observable<Job> {
    return this.http.get<Job>(`/api/jobs/${encodeURIComponent(name)}`);
  }

  createJob(request: JobRequest): Observable<Job> {
    return this.http.post<Job>('/api/jobs', request);
  }

  pause(name: string): Observable<Job> {
    return this.http.post<Job>(`/api/jobs/${encodeURIComponent(name)}/pause`, null);
  }

  resume(name: string): Observable<Job> {
    return this.http.post<Job>(`/api/jobs/${encodeURIComponent(name)}/resume`, null);
  }

  trigger(name: string): Observable<Run> {
    return this.http.post<Run>(`/api/jobs/${encodeURIComponent(name)}/runs`, null);
  }

  history(name: string): Observable<Revision[]> {
    return this.http
      .get<Embedded<'revisions', Revision>>(`/api/jobs/${encodeURIComponent(name)}/history`)
      .pipe(map((body) => body._embedded?.revisions ?? []));
  }

  runs(filter: { job?: string; status?: RunStatus | ''; size?: number } = {}): Observable<RunPage> {
    let params = new HttpParams().set('size', filter.size ?? 50);
    if (filter.job) {
      params = params.set('job', filter.job);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    return this.http.get<Embedded<'runs', Run>>('/api/runs', { params }).pipe(
      map((body) => ({
        runs: body._embedded?.runs ?? [],
        totalElements: body.page?.totalElements ?? 0,
      })),
    );
  }
}
