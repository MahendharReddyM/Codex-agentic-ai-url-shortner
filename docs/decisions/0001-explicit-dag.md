# ADR 0001: Use an explicit workflow DAG

**Status:** Accepted

## Context

The workflow must support sequencing, parallel work, synchronization, retries, approvals, rollback,
and replanning when upstream output changes. A controller-coded sequence or prompt chain hides these
relationships and makes impact analysis unreliable.

## Decision

Represent each stage with identity, type, dependencies, approval policy, attempt budget, and optional
fallback. Validate all references, entry/exit gates, and acyclicity. Determine readiness from completed
dependencies and compute transitive downstream impact from the same graph.

## Consequences

The control flow is inspectable, testable, and safe to replan. Additional graph evolution/versioning
is required once runs must survive application releases. A production system would store the graph
snapshot with each run and migrate definitions only for new runs.

