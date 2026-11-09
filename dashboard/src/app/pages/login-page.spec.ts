import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  async function signIn(next: string | undefined) {
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    if (next !== undefined) {
      fixture.componentRef.setInput('next', next);
    }
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    for (const [name, value] of [
      ['username', 'admin'],
      ['password', 'secret'],
    ]) {
      const input = el.querySelector(`[formcontrolname="${name}"]`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/login').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/me').flush({ username: 'admin', roles: ['ADMIN'], canChange: true });
    return navigate;
  }

  it('returns to the page the user wanted', async () => {
    expect(await signIn('/runs')).toHaveBeenCalledWith('/runs');
  });

  it('goes to the job list by default', async () => {
    expect(await signIn(undefined)).toHaveBeenCalledWith('/jobs');
  });

  it('never follows a next link to another site', async () => {
    expect(await signIn('//evil.example')).toHaveBeenCalledWith('/jobs');
    TestBed.resetTestingModule();
    expect(await signIn('https://evil.example')).toHaveBeenCalledWith('/jobs');
  });
});
