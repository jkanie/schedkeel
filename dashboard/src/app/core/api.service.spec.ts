import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { aJob, aRun } from '../testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('unwraps the HAL job list', () => {
    let names: string[] = [];
    api.jobs().subscribe((jobs) => (names = jobs.map((j) => j.name)));
    http.expectOne('/api/jobs').flush({ _embedded: { jobs: [aJob(), aJob({ name: 'b' })] } });
    expect(names).toEqual(['nightly-report', 'b']);
  });

  it('treats a collection without _embedded as empty', () => {
    let count = -1;
    api.jobs().subscribe((jobs) => (count = jobs.length));
    http.expectOne('/api/jobs').flush({ _links: {} });
    expect(count).toBe(0);
  });

  it('sends run filters as query parameters and leaves out empty ones', () => {
    let total = 0;
    api
      .runs({ job: 'nightly-report', status: '' })
      .subscribe((page) => (total = page.totalElements));
    const req = http.expectOne((r) => r.url === '/api/runs');
    expect(req.request.params.get('job')).toBe('nightly-report');
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ _embedded: { runs: [aRun()] }, page: { totalElements: 7 } });
    expect(total).toBe(7);
  });

  it('escapes job names in paths', () => {
    api.trigger('a b').subscribe();
    const req = http.expectOne('/api/jobs/a%20b/runs');
    expect(req.request.method).toBe('POST');
    req.flush(aRun());
  });
});
