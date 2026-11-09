import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { aRun } from '../testing';
import { REFRESH_MS, RunsPage } from './runs-page';

describe('RunsPage', () => {
  // Fake timers drive the poll; detectChanges renders without waiting for a stable app.
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function setUp() {
    TestBed.configureTestingModule({
      imports: [RunsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(RunsPage);
    return {
      fixture,
      http: TestBed.inject(HttpTestingController),
      el: fixture.nativeElement as HTMLElement,
    };
  }

  it('loads runs and refreshes on a timer', () => {
    const { fixture, http, el } = setUp();
    vi.advanceTimersByTime(0);
    http
      .expectOne((r) => r.url === '/api/runs')
      .flush({
        _embedded: { runs: [aRun({ id: 3, status: 'QUEUED' })] },
        page: { totalElements: 1 },
      });
    fixture.detectChanges();
    expect(el.textContent).toContain('#3');
    expect(el.textContent).toContain('queued');

    vi.advanceTimersByTime(REFRESH_MS);
    http
      .expectOne((r) => r.url === '/api/runs')
      .flush({
        _embedded: { runs: [aRun({ id: 3, status: 'SUCCEEDED' })] },
        page: { totalElements: 1 },
      });
    fixture.detectChanges();
    expect(el.textContent).toContain('succeeded');
  });

  it('filters by status at once', () => {
    const { fixture, http } = setUp();
    vi.advanceTimersByTime(0);
    http.expectOne((r) => r.url === '/api/runs').flush({ page: { totalElements: 0 } });
    fixture.detectChanges();

    const select = (fixture.nativeElement as HTMLElement).querySelector(
      'select',
    ) as HTMLSelectElement;
    select.value = 'FAILED';
    select.dispatchEvent(new Event('change'));
    vi.advanceTimersByTime(0);

    const req = http.expectOne((r) => r.url === '/api/runs');
    expect(req.request.params.get('status')).toBe('FAILED');
    req.flush({ page: { totalElements: 0 } });
  });

  it('shows a run’s output when its row is clicked', () => {
    const { fixture, http, el } = setUp();
    vi.advanceTimersByTime(0);
    http
      .expectOne((r) => r.url === '/api/runs')
      .flush({
        _embedded: { runs: [aRun({ output: 'HTTP 500\nboom' })] },
        page: { totalElements: 1 },
      });
    fixture.detectChanges();
    (el.querySelector('tbody tr') as HTMLElement).click();
    fixture.detectChanges();
    expect(el.querySelector('pre')?.textContent).toBe('HTTP 500\nboom');
  });
});
