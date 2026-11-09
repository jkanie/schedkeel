import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { aJob } from '../testing';
import { JobForm, sixFieldCron } from './job-form';

describe('sixFieldCron', () => {
  it('accepts 6 fields with any spacing', () => {
    expect(sixFieldCron(new FormControl(' 0  0 2 * * * ', { nonNullable: true }))).toBeNull();
  });

  it('rejects the 5-field Unix form', () => {
    expect(sixFieldCron(new FormControl('0 2 * * *', { nonNullable: true }))).toEqual({
      sixFieldCron: { fields: 5 },
    });
  });
});

describe('JobForm', () => {
  function setUp() {
    TestBed.configureTestingModule({
      imports: [JobForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(JobForm);
    fixture.detectChanges();
    return {
      fixture,
      http: TestBed.inject(HttpTestingController),
      el: fixture.nativeElement as HTMLElement,
    };
  }

  function type(el: HTMLElement, name: string, value: string) {
    const input = el.querySelector(`[formcontrolname="${name}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  it('keeps Create disabled until the form is valid', async () => {
    const { fixture, el } = setUp();
    const create = el.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(create.disabled).toBe(true);

    type(el, 'name', 'ping');
    type(el, 'cron', '0 * * * * *');
    type(el, 'target', 'hello');
    await fixture.whenStable();
    expect(create.disabled).toBe(false);
  });

  it('posts the job and emits it', async () => {
    const { fixture, http, el } = setUp();
    let created = '';
    fixture.componentInstance.created.subscribe((job) => (created = job.name));
    type(el, 'name', 'ping');
    type(el, 'cron', '0 * * * * *');
    type(el, 'target', 'hello');
    await fixture.whenStable();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const req = http.expectOne('/api/jobs');
    expect(req.request.body).toEqual({
      name: 'ping',
      cron: '0 * * * * *',
      timeZone: 'UTC',
      type: 'LOG',
      target: 'hello',
      maxRetries: 0,
      timeoutSeconds: 300,
    });
    req.flush(aJob({ name: 'ping' }));
    expect(created).toBe('ping');
  });

  it('shows the target format for the chosen type', async () => {
    const { fixture, el } = setUp();
    const select = el.querySelector('[formcontrolname="type"]') as HTMLSelectElement;
    select.value = 'AMQP';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    const target = el.querySelector('[formcontrolname="target"]') as HTMLInputElement;
    expect(target.placeholder).toBe('exchange/routing-key, for example billing/invoice.due');
  });

  it('shows the server problem detail when creation fails', async () => {
    const { fixture, http, el } = setUp();
    type(el, 'name', 'ping');
    type(el, 'cron', '0 * * * * *');
    type(el, 'target', 'hello');
    await fixture.whenStable();
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/jobs')
      .flush(
        { status: 409, detail: "A job named 'ping' already exists." },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      "A job named 'ping' already exists.",
    );
  });
});
