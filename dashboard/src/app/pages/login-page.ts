import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/problem';

@Component({
  selector: 'sk-login-page',
  imports: [ReactiveFormsModule],
  template: `
    <form class="card" [formGroup]="form" (ngSubmit)="submit()">
      <h1>Sign in</h1>
      <label>
        User
        <input formControlName="username" autocomplete="username" required />
      </label>
      <label>
        Password
        <input
          formControlName="password"
          type="password"
          autocomplete="current-password"
          required
        />
      </label>
      @if (error(); as message) {
        <p class="error" role="alert">{{ message }}</p>
      }
      <button type="submit" [disabled]="form.invalid || busy()">Sign in</button>
    </form>
  `,
  styles: `
    .card {
      max-width: 360px;
      margin: 64px auto;
      display: grid;
      gap: 16px;
    }
    h1 {
      margin: 0;
    }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Where to go after signing in. Bound from the ?next= query parameter. */
  readonly next = input<string>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    const { username, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.auth.signIn(username, password).subscribe({
      next: (ok) => {
        this.busy.set(false);
        if (ok) {
          void this.router.navigateByUrl(this.safeNext());
        } else {
          this.error.set('That user and password do not match.');
        }
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.error.set(describeError(e));
      },
    });
  }

  /** Only follows in-app paths, so ?next= cannot send the user to another site. */
  private safeNext(): string {
    const next = this.next();
    return next && next.startsWith('/') && !next.startsWith('//') ? next : '/jobs';
  }
}
