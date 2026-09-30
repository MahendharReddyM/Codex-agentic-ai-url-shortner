# ADR 0002: Isolate zero-infrastructure adapters behind ports

**Status:** Accepted for prototype

## Context

Reviewers need a one-command runnable service. Adding external databases and brokers would increase
setup cost but would not by itself demonstrate better domain or orchestration reasoning.

## Decision

Use thread-safe, bounded in-memory adapters behind the URL product's `ShortLinkRepository` and
`AnalyticsRepository`, and behind the legacy graph-visualization `WorkflowRunRepository`. Keep time
and code generation injectable. The evidence-producing engineering workflow instead uses the
`EngineeringRunStore` port with atomic JSON and PostgreSQL implementations because assessment runs
must survive restart. Do not describe any process-local product data as production ready.

## Consequences

The URL service remains deterministic and easy to review but its demo links/analytics do not survive
restart. Engineering-run state does survive restart, while concurrent multi-worker execution still
requires versioned leases. Redis, event streaming, managed PostgreSQL and append-only audit storage
are required before real deployment, but the domain services and tests remain stable through that
migration.

