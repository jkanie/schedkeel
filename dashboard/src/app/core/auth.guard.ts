import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/** Sends signed-out visitors to the sign-in page, then back where they were going. */
export const signedIn: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .check()
    .pipe(
      map((me) =>
        me ? true : router.createUrlTree(['/login'], { queryParams: { next: state.url } }),
      ),
    );
};
