import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="bar">
      <a class="brand" routerLink="/jobs" aria-label="schedkeel home">
        <span class="mark" aria-hidden="true"></span>schedkeel
      </a>
      @if (auth.me(); as me) {
        <nav aria-label="Main">
          <a routerLink="/jobs" routerLinkActive="active">Jobs</a>
          <a routerLink="/runs" routerLinkActive="active">Runs</a>
        </nav>
        <div class="who">
          <span
            >{{ me.username }} <small>{{ me.roles.join(', ') }}</small></span
          >
          <button type="button" class="ghost" (click)="signOut()">Sign out</button>
        </div>
      }
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .bar {
      display: flex;
      align-items: center;
      gap: 24px;
      padding: 12px 24px;
      border-bottom: 1px solid var(--line);
      background: var(--panel);
      position: sticky;
      top: 0;
      z-index: 1;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 700;
      letter-spacing: 0.02em;
      color: var(--ink);
      text-decoration: none;
    }
    .mark {
      width: 14px;
      height: 14px;
      border-radius: 3px 3px 7px 7px;
      background: var(--accent);
    }
    nav {
      display: flex;
      gap: 4px;
    }
    nav a {
      padding: 6px 12px;
      border-radius: 6px;
      color: var(--muted);
      text-decoration: none;
    }
    nav a.active {
      color: var(--ink);
      background: var(--chip);
    }
    .who {
      margin-left: auto;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .who small {
      color: var(--muted);
    }
    main {
      max-width: 1120px;
      margin: 0 auto;
      padding: 24px 16px 48px;
    }
    @media (max-width: 600px) {
      .bar {
        flex-wrap: wrap;
        gap: 8px 16px;
        padding: 10px 16px;
      }
      .who {
        margin-left: 0;
      }
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected signOut(): void {
    this.auth.signOut().subscribe(() => void this.router.navigate(['/login']));
  }
}
