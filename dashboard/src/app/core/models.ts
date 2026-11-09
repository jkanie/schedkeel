/** Shapes of the schedkeel API (HAL). Only the fields the dashboard uses. */

export type JobType = 'LOG' | 'HTTP' | 'KAFKA' | 'AMQP' | 'BATCH';
export type Concurrency = 'ALLOW' | 'FORBID';
export type RunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'SKIPPED';

export const RUN_STATUSES: RunStatus[] = [
  'QUEUED',
  'RUNNING',
  'SUCCEEDED',
  'FAILED',
  'TIMED_OUT',
  'SKIPPED',
];

export interface Link {
  href: string;
  templated?: boolean;
}

export interface Job {
  name: string;
  description: string | null;
  cron: string;
  timeZone: string;
  type: JobType;
  target: string;
  payload: string | null;
  enabled: boolean;
  concurrency: Concurrency;
  maxRetries: number;
  retryBackoffSeconds: number;
  timeoutSeconds: number;
  createdAt: string;
  createdBy: string | null;
  updatedAt: string;
  updatedBy: string | null;
  _links: Record<string, Link>;
}

export interface JobRequest {
  name: string;
  description?: string;
  cron: string;
  timeZone?: string;
  type: JobType;
  target: string;
  payload?: string;
  concurrency?: Concurrency;
  maxRetries?: number;
  retryBackoffSeconds?: number;
  timeoutSeconds?: number;
}

export interface Run {
  id: number;
  jobName: string;
  status: RunStatus;
  trigger: 'SCHEDULE' | 'MANUAL' | 'KAFKA' | 'AMQP' | 'RETRY';
  attempt: number;
  scheduledFor: string;
  notBefore: string;
  startedAt: string | null;
  finishedAt: string | null;
  owner: string | null;
  output: string | null;
}

export interface RunPage {
  runs: Run[];
  totalElements: number;
}

export interface Revision {
  revision: number;
  change: 'INSERT' | 'UPDATE' | 'DELETE';
  changedAt: string;
  changedBy: string | null;
  job: Omit<Job, '_links'>;
}

export interface Me {
  username: string;
  roles: string[];
  canChange: boolean;
}

/** An RFC 9457 problem detail, as the API returns for errors. */
export interface Problem {
  status: number;
  detail?: string;
  title?: string;
}
