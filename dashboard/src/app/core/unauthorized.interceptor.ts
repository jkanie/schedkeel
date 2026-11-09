import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** When the session ends mid-use, the next 401 takes the user to sign in. */
export const unauthorizedInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      const quiet = request.url === '/api/me' || request.url === '/api/login';
      if (error instanceof HttpErrorResponse && error.status === 401 && !quiet) {
        auth.forget();
        void router.navigate(['/login'], { queryParams: { next: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
