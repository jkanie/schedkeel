# Roadmap

Planned work after the seed, with an hour estimate per item. Each item brings in 1 more Spring project (or 2 that belong together) and puts it to real use. The total is 73.5 hours.

## 1. Spring AMQP: RabbitMQ jobs and triggers, 2.5 hours

- What: an `AMQP` job type that publishes the payload to a RabbitMQ exchange, plus a queue whose messages start runs, as the Kafka trigger does. Tests run RabbitMQ through Testcontainers.
- Why: many teams run RabbitMQ, not Kafka. Today, they cannot start or feed jobs from it.

## 2. Spring for Apache Pulsar: Pulsar jobs, 2 hours

- What: a `PULSAR` job type that sends the payload to a Pulsar topic, with a Testcontainers test.
- Why: it rounds out the message brokers a job can feed, with the same job settings and retries.

## 3. Spring Integration: start a job when a file arrives, 3 hours

- What: a file-drop trigger. A directory (local, then SFTP) is watched, and each new file queues a run with the file path as the payload. Files that were already seen are kept in a metadata store, so a restart does not start them again.
- Why: many batch jobs wait for a partner's file. Today, that takes a cron job that polls and guesses.

## 4. Spring Web Services: SOAP jobs, 3 hours

- What: a `SOAP` job type that sends the payload as a SOAP request through `WebServiceTemplate`, with a test against a SOAP endpoint in the test context.
- Why: older systems in banks, insurers, the public sector still only speak SOAP.

## 5. Spring gRPC: a gRPC API for runs, 3 hours

- What: a gRPC service with `TriggerRun` and a server stream of run events, next to the REST API, with tests through an in-process channel.
- Why: services written in other languages get a typed, fast way to start runs and follow them.

## 6. Spring for GraphQL: 1 query for the dashboard, 3 hours

- What: a GraphQL schema for jobs, runs, history, with a subscription for finished runs. The job page then loads with 1 request instead of 3.
- Why: the job page makes 3 calls today. GraphQL also lets other clients ask for only the fields they need.

## 7. Spring Web Flow: a guided New job wizard, 3 hours

- What: a server-side wizard (Thymeleaf pages) in 4 steps: type, target, schedule (with a preview of the next 5 fire times), review. Each step checks its own fields before the next.
- Why: a cron expression in a time zone is easy to get wrong. A preview of the next fire times before saving catches the mistake early.

## 8. Spring LDAP and Spring Data LDAP: company sign-in, 3 hours

- What: sign-in against an LDAP directory, with directory groups mapped to the VIEWER and ADMIN roles. Tests run against an embedded UnboundID directory.
- Why: today, the 2 users come from settings. Companies keep their users in a directory.

## 9. Project Reactor: live updates without polling, 2.5 hours

- What: a Server-Sent Events stream of finished runs, fed by a Reactor `Sinks.Many` from the `RunFinished` event. The Runs page listens to it instead of polling every 5 seconds.
- Why: polling costs 12 requests a minute per open page, and a result still shows up to 5 seconds late.

## 10. Spring Data R2DBC: non-blocking run statistics, 3 hours

- What: a read-only statistics endpoint (runs per status per job, over a time window) on R2DBC, for the SSE stream and the dashboard.
- Why: statistics queries over long histories should not hold the threads that serve the main API.

## 11. Spring Cloud Gateway: 1 entry point for many instances, 3 hours

- What: a `gateway` module that routes to several server instances, with a per-user rate limit backed by Redis.
- Why: with several instances, the dashboard and the CLI need 1 address. The rate limit protects the API from a script in a loop.

## 12. Spring Cloud Config: settings from Git, 2.5 hours

- What: a `config-server` module that serves the `schedkeel.*` settings from a Git repository. Server instances read their settings from it at start-up.
- Why: settings such as the HTTP allow-list must match on every instance. Git gives them a history and a review.

## 13. Spring Cloud Bus: change settings on every instance at once, 1.5 hours

- What: a refresh event over Kafka, so 1 call reloads the settings on all instances.
- Why: without it, a settings change needs a restart of each instance.

## 14. Spring Cloud Stream: broker-neutral events, 2.5 hours

- What: move the trigger listener and the run-events publisher to Spring Cloud Stream bindings, so the same code runs on Kafka or RabbitMQ by configuration.
- Why: teams pick the broker they already run, with no code change.

## 15. Spring Cloud Task: short-lived worker apps, 3 hours

- What: a worker kit for jobs that run as their own short-lived Spring Boot app, recorded by Spring Cloud Task, plus a `TASK` job type that starts one and reads its exit code.
- Why: heavy jobs (reports, imports) should not run inside the scheduler's process.

## 16. Spring Cloud Function: jobs as plain functions, 2.5 hours

- What: a `FUNCTION` job type that runs a `Function<String, String>` bean by name, with the payload as input and the result as output.
- Why: small plug-in jobs then need 1 bean and no HTTP endpoint.

## 17. Spring Cloud OpenFeign: an API client for the worker kit, 1.5 hours

- What: a declarative Feign client for the schedkeel API, used by the worker kit to report progress on long runs.
- Why: worker apps need a typed client. Feign gives one with very little code.

## 18. Spring Cloud Kubernetes: run on Kubernetes, 3 hours

- What: Kubernetes manifests, settings from ConfigMaps, leader election through Kubernetes leases as an option next to the Redis lease.
- Why: on Kubernetes, the platform already offers leases and settings. Redis then only holds sessions.

## 19. Spring Cloud Zookeeper: leader election with ZooKeeper, 2 hours

- What: the leader lease behind 1 interface, with a ZooKeeper option, tested through Testcontainers.
- Why: some companies already run ZooKeeper for this, and do not want Redis for it.

## 20. Spring Cloud Consul: discovery and settings with Consul, 2 hours

- What: server instances register in Consul, and the gateway finds them there.
- Why: with discovery, new instances join the gateway without a settings change.

## 21. Spring Cloud Netflix Eureka: discovery with Eureka, 2 hours

- What: Eureka as a 2nd discovery option for the gateway, for teams that already run it.
- Why: Eureka is still common in older Spring Cloud estates.

## 22. Spring Data MongoDB: full run output, 3 hours

- What: keep each run's full output in MongoDB (GridFS for large output). PostgreSQL keeps the first 4,000 characters, as today.
- Why: today, output past 4,000 characters is cut off, and long logs are where the useful errors are.

## 23. Spring Data Elasticsearch: search run output, 3 hours

- What: index run output in Elasticsearch, with a search box on the Runs page.
- Why: finding which runs failed with a given error message should take 1 search, not a scroll through pages.

## 24. Spring Data Neo4j: jobs that wait for other jobs, 4 hours

- What: job dependencies (B runs after A succeeds) stored as a graph in Neo4j, with a check that refuses cycles, plus a graph view on the job page.
- Why: real pipelines are chains of jobs. Today, each job needs its own guessed start time.

## 25. Spring Data KeyValue: blackout windows, 2 hours

- What: dates and hours when no job may start (holidays, change freezes), kept in a KeyValue repository and loaded from settings. Due runs inside a window are recorded as `SKIPPED`.
- Why: many teams must not run batch work on holidays or during a release.

## 26. Spring Data Cassandra: a high-volume run log, 3 hours

- What: an optional run log in Cassandra for installs with millions of runs a day, behind the same run repository interface.
- Why: PostgreSQL works well up to a point. Very large installs need a store built for append-heavy time series.

## 27. Spring Data REST: an admin API over the repositories, 2 hours

- What: read-only HAL endpoints over the event publication log and the batch metadata, with the HAL Explorer.
- Why: operators can then check stuck events and batch history without database access.

## 28. Spring Initializr: a starter for worker apps, 3 hours

- What: a small Initializr instance that generates worker projects with the worker kit, the Feign client, a sample function already set up.
- Why: a new worker app should take 1 minute to start, not an afternoon of copying set-up.
