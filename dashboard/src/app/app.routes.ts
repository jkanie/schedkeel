import { Routes } from '@angular/router';
import { signedIn } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'jobs' },
  {
    path: 'login',
    title: 'Sign in · schedkeel',
    loadComponent: () => import('./pages/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'jobs',
    title: 'Jobs · schedkeel',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/jobs-page').then((m) => m.JobsPage),
  },
  {
    path: 'jobs/:name',
    title: 'Job · schedkeel',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/job-page').then((m) => m.JobPage),
  },
  {
    path: 'runs',
    title: 'Runs · schedkeel',
    canActivate: [signedIn],
    loadComponent: () => import('./pages/runs-page').then((m) => m.RunsPage),
  },
  { path: '**', redirectTo: 'jobs' },
];
