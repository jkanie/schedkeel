import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('treats a 401 from /api/me as signed out', () => {
    let result: unknown = 'unset';
    auth.check().subscribe((me) => (result = me));
    http.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(result).toBeNull();
    expect(auth.canChange()).toBe(false);
  });

  it('asks the server only once', () => {
    auth.check().subscribe();
    http.expectOne('/api/me').flush({ username: 'viewer', roles: ['VIEWER'], canChange: false });
    auth.check().subscribe();
    http.expectNone('/api/me');
  });

  it('posts the sign-in form, then loads who signed in', () => {
    let ok: boolean | undefined;
    auth.signIn('admin', 'p&ss').subscribe((result) => (ok = result));

    const login = http.expectOne('/api/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body.toString()).toBe('username=admin&password=p%26ss');
    login.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/me').flush({ username: 'admin', roles: ['ADMIN'], canChange: true });

    expect(ok).toBe(true);
    expect(auth.canChange()).toBe(true);
  });

  it('reports a wrong password as false', () => {
    let ok: boolean | undefined;
    auth.signIn('admin', 'wrong').subscribe((result) => (ok = result));
    http.expectOne('/api/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(ok).toBe(false);
  });
});
