import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { Me } from './models';

/**
 * Who is signed in. The server keeps the session (in Redis); the dashboard
 * only remembers the answer to GET /api/me.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  /** undefined until checked, null when signed out. */
  readonly me = signal<Me | null | undefined>(undefined);
  readonly canChange = computed(() => this.me()?.canChange ?? false);

  /** Asks the server once per page load. A 401 means signed out. */
  check(): Observable<Me | null> {
    const known = this.me();
    if (known !== undefined) {
      return of(known);
    }
    return this.http.get<Me>('/api/me').pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          return of(null);
        }
        throw error;
      }),
      tap((me) => this.me.set(me)),
    );
  }

  /** Posts the sign-in form. Resolves to false for a wrong user or password. */
  signIn(username: string, password: string): Observable<boolean> {
    const body = new HttpParams().set('username', username).set('password', password);
    return this.http.post('/api/login', body).pipe(
      switchMap(() => {
        this.me.set(undefined);
        return this.check();
      }),
      map((me) => me !== null),
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          return of(false);
        }
        throw error;
      }),
    );
  }

  signOut(): Observable<void> {
    return this.http.post<void>('/api/logout', null).pipe(tap(() => this.me.set(null)));
  }

  /** Called when any request comes back 401, for example after the session expired. */
  forget(): void {
    this.me.set(null);
  }
}
