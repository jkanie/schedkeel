import { Job, Run } from './core/models';

/** Builders for test data, with every field the API sends. */
export function aJob(overrides: Partial<Job> = {}): Job {
  return {
    name: 'nightly-report',
    description: null,
    cron: '0 0 2 * * *',
    timeZone: 'UTC',
    type: 'HTTP',
    target: 'https://example.org/build',
    payload: null,
    enabled: true,
    concurrency: 'FORBID',
    maxRetries: 0,
    retryBackoffSeconds: 30,
    timeoutSeconds: 300,
    createdAt: '2026-03-01T12:00:00Z',
    createdBy: 'admin',
    updatedAt: '2026-03-01T12:00:00Z',
    updatedBy: 'admin',
    _links: {},
    ...overrides,
  };
}

export function aRun(overrides: Partial<Run> = {}): Run {
  return {
    id: 1,
    jobName: 'nightly-report',
    status: 'SUCCEEDED',
    trigger: 'SCHEDULE',
    attempt: 1,
    scheduledFor: '2026-03-01T02:00:00Z',
    notBefore: '2026-03-01T02:00:00Z',
    startedAt: '2026-03-01T02:00:00Z',
    finishedAt: '2026-03-01T02:00:03Z',
    owner: 'server-1',
    output: 'HTTP 200',
    ...overrides,
  };
}
