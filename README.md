# Agentic URL Shortener

A Java 21/Spring Boot URL shortener plus a governed software-engineering execution system. The
primary `/api/v1/engineering` workflow reads a real repository, invokes specialized agents through a
provider-neutral LLM boundary, applies a bounded patch in an isolated workspace, executes a fixed
build command, repairs from failure evidence, and stops at hash-bound human approval gates.

This public repository contains original assessment work only—no client data, credentials, or
internal material.

## What is implemented

- URL create, inspect, redirect, expiry, admin-protected disable/analytics and privacy-reduced click data.
- Repository-aware greenfield, brownfield and ambiguous engineering scenarios.
- OpenAI Responses API adapter with strict structured output, plus an explicit deterministic test
  double for offline demonstrations and repeatable tests.
- Isolated baseline/workspace copies, protected paths, optimistic file hashes, secret scanning,
  bounded changes and exact-manifest rollback.
- Sequential planning, parallel implementation/test/documentation branches, a synchronization
  gate, bounded model/build timeouts and at most three validation attempts.
- Executable Maven/Gradle validation. A model cannot declare tests passed: the gate requires a
  successful process, parsed test count and hashed Surefire/Gradle XML reports.
- Authenticated roles and separation of duties. An independent approver must approve the exact plan
  hash and later the exact validated-outcome hash.
- Durable JSON persistence by default and a PostgreSQL adapter used by Docker Compose.
- Hash-chained audit events and reliability measures for success, retries, rollbacks, recovery time
  and end-to-end latency.
- CI verification with line/branch coverage gates, SpotBugs, test/coverage artifact retention and
  high-severity dependency review on pull requests.

## Execution chain

```text
authenticated requirement
        |
        v
policy entry gate -> isolated workspace -> repository map
        |
        v
requirements -> [impact analysis for brownfield] -> architecture -> security
        |
        v
CLARIFICATION GATE (ambiguous only) -> CHANGE APPROVAL (exact plan hash)
        |
        +-------------------+-------------------+
        v                   v                   v
 implementation       executable tests       documentation
        +-------------------+-------------------+
                            |
                            v
                      governed patch
                            |
                            v
            fixed build/test -> repair -> retry (bounded)
                            |
                            v
            post-change security + release-readiness
                            |
                            v
             evidence synchronization/quality gate
                            |
                            v
               RELEASE APPROVAL (exact outcome hash)
                            |
                            v
                       RELEASE_READY
```

Any policy, model, patch, timeout or build failure safe-stops or restores the exact baseline. A
changed upstream requirement creates a new revision, invalidates stale approvals/artifacts and
re-enters the governed chain.

## Run locally

Prerequisite: JDK 21. The included Maven wrapper downloads Maven automatically.

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Open [Swagger UI](http://localhost:8080/swagger-ui.html) or run `docker compose up --build` to use
the PostgreSQL-backed configuration. The compose service mounts this repository read-only and gives
each run a separate writable workspace.

Local demonstration identities (override all passwords outside a local demo):

| User | Password | Authority |
|---|---|---|
| `submitter` | `local-submitter-only` | start, replan and cancel |
| `approver` | `local-approver-only` | approve and review metrics |
| `admin` | `local-admin-only` | administrative access |

## Run the engineering chain

Start a run as the submitter. `repositoryPath` must resolve below `AGENTIC_REPOSITORY_ROOT`.

```bash
curl -u submitter:local-submitter-only -X POST http://localhost:8080/api/v1/engineering/runs \
  -H "Content-Type: application/json" \
  -d '{"requirement":"Add expiring links with executable tests and reviewer documentation.","scenario":"BROWNFIELD","repositoryPath":".","baselineRevision":"WORKING_TREE"}'
```

Copy the returned `id` and `planHash`, then approve as a different identity:

```bash
curl -u approver:local-approver-only -X POST \
  http://localhost:8080/api/v1/engineering/runs/RUN_ID/approvals/change \
  -H "Content-Type: application/json" \
  -d '{"artifactHash":"PLAN_HASH","comment":"Plan and repository evidence reviewed"}'
```

The result contains repository symbols, every provider/model invocation, patch and artifact hashes,
build commands, test-report hashes, validation decisions, the audit chain and an independently
recomputed `auditChainValid` flag. Approve the returned
`outcomeHash` at `/approvals/release`; release approval marks readiness and never deploys.

For a real model invocation:

```bash
export AGENTIC_MODEL_PROVIDER=openai
export AGENTIC_MODEL_NAME=gpt-5
export OPENAI_API_KEY=...
./mvnw spring-boot:run
```

Without those settings, `deterministic` is the transparent offline test double. It exercises the
same schemas, policies, patching, build, repair, approval and persistence path; it is not presented
as an external LLM invocation.

## Main APIs

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/links` | Create an idempotent short link |
| `GET` | `/r/{code}` | Redirect and record privacy-reduced analytics |
| `GET` | `/api/v1/links/{code}/analytics` | Aggregate analytics |
| `POST` | `/api/v1/engineering/runs` | Analyze, plan and pause at the change gate |
| `POST` | `/api/v1/engineering/runs/{id}/approvals/change` | Approve the exact plan and execute |
| `POST` | `/api/v1/engineering/runs/{id}/approvals/release` | Approve the exact validated outcome |
| `POST` | `/api/v1/engineering/runs/{id}/changes` | Revise an upstream requirement and replan |
| `POST` | `/api/v1/engineering/runs/{id}/cancel` | Safe-stop and restore the baseline |
| `GET` | `/api/v1/engineering/runs/{id}` | Inspect complete execution evidence |
| `GET` | `/api/v1/engineering/metrics` | Inspect reliability measures |

`/api/v1/orchestration` remains as the earlier graph-model compatibility API. Assessment of actual
repository mutation and build evidence should use `/api/v1/engineering`.

## Documentation

- [Architecture and control flow](docs/ARCHITECTURE.md)
- [Execution evidence for all three scenarios](docs/SCENARIOS.md)
- [Feedback remediation matrix](docs/ASSESSMENT_FEEDBACK_REMEDIATION.md)
- [Testing and release gates](docs/TESTING.md)
- [Threat model](docs/THREAT_MODEL.md)
- [Interview walkthrough](docs/INTERVIEW_WALKTHROUGH.md)
- [File inventory and use cases](docs/FILE_GUIDE.md)
- [Ready-made HTTP demonstration](demo/assessment.http)

## Verify

```bash
./mvnw verify
```

The suite covers the URL service and execution chain, including authorization, dynamic repository
mapping, prompt/secret handling, path protection, exact-hash approvals, parallel work, repair,
rollback, replanning, ambiguity and restart durability. The history intentionally remains exactly
seven cohesive commits; the final commit is the hardened, evidence-producing implementation.

## Honest prototype boundary

The system produces a reviewable release candidate, not a production deployment. It does not grant
agents shell access or deployment authority. The OpenAI adapter requires a caller-provided key; CI
uses the deterministic test double. PostgreSQL persists run state, while production would also add
OIDC-backed identities, signed append-only audit export, distributed workers with leases, a hardened
container sandbox, controlled egress and organization-specific change-management integration.
