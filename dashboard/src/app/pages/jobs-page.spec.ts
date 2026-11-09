import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Me } from '../core/models';
import { aJob } from '../testing';
import { JobsPage } from './jobs-page';

describe('JobsPage', () => {
  async function render(me: Me) {
    TestBed.configureTestingModule({
      imports: [JobsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(AuthService).me.set(me);
    const fixture = TestBed.createComponent(JobsPage);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/jobs').flush({
      _embedded: { jobs: [aJob(), aJob({ name: 'paused-one', enabled: false, type: 'LOG' })] },
    });
    await fixture.whenStable();
    return { fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  const admin: Me = { username: 'admin', roles: ['ADMIN'], canChange: true };
  const viewer: Me = { username: 'viewer', roles: ['VIEWER'], canChange: false };

  it('lists every job with its state', async () => {
    const { el } = await render(viewer);
    const rows = Array.from(el.querySelectorAll('tbody tr')).map((r) => r.textContent ?? '');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('nightly-report');
    expect(rows[0]).toContain('enabled');
    expect(rows[1]).toContain('paused');
  });

  it('hides change buttons from viewers', async () => {
    const { el } = await render(viewer);
    expect(el.textContent).not.toContain('Run now');
    expect(el.textContent).not.toContain('New job');
  });

  it('lets admins run a job now and reloads the list', async () => {
    const { fixture, http, el } = await render(admin);
    const runNow = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent === 'Run now',
    )!;
    runNow.click();

    http.expectOne({ method: 'POST', url: '/api/jobs/nightly-report/runs' }).flush({ id: 12 });
    http.expectOne('/api/jobs').flush({ _embedded: { jobs: [aJob()] } });
    await fixture.whenStable();
    expect(el.querySelector('[role="status"]')?.textContent).toContain(
      'Queued run 12 of nightly-report.',
    );
  });

  it('offers Resume for a paused job', async () => {
    const { el } = await render(admin);
    const second = el.querySelectorAll('tbody tr')[1];
    expect(second.textContent).toContain('Resume');
    expect(second.textContent).not.toContain('Pause');
  });
});
