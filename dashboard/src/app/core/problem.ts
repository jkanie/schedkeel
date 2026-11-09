import { HttpErrorResponse } from '@angular/common/http';
import { Problem } from './models';

/** 1 readable line for any API error. */
export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as Problem | null;
    if (problem && typeof problem === 'object' && problem.detail) {
      return problem.detail;
    }
    if (error.status === 0) {
      return 'The server did not answer. Is it running?';
    }
    if (error.status === 403) {
      return 'Your account may only read. Ask an admin to make this change.';
    }
    return `The server answered ${error.status}.`;
  }
  return 'Something went wrong.';
}
