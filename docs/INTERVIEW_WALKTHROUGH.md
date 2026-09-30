# Interview walkthrough and design defense

## 45-second opening

“This repository contains a working URL shortener and a governed engineering execution system. The
important point is that it is not a prompt chain: it snapshots and maps a repository, invokes typed
specialists through a provider-neutral model boundary, pauses for an independent approval bound to
the exact plan hash, runs implementation, tests and documentation in parallel, applies a policy-
checked patch in an isolated workspace, executes a fixed build, repairs from real failure evidence,
and requires a second exact-hash approval. Every decision, diff, process result, test report and
rollback is durable and hash-linked. Agents prepare a release candidate; humans own scope, code
review, release and deployment.”

## Suggested 20-minute demonstration

### 1. Product outcome — 2 minutes

Create and redirect a short link, show analytics, then mention expiry, disable, idempotency, URL
safety, privacy pseudonyms, rate limits, bounded async analytics and lock-striped creation.

### 2. Execution architecture — 4 minutes

Open `docs/ARCHITECTURE.md`, trace the graph and point to these boundaries:

- `RepositoryWorkspaceService`: baseline/isolation/revision/path constraints;
- `RepositoryAnalyzer`: actual files, dependencies, symbols, snippets and hashes;
- `LlmClient` / `StructuredAgentGateway`: real OpenAI adapter, test double, strict schema/evidence;
- `GovernedPatchApplier`: model output becomes data, never a command;
- `FixedBuildExecutor`: the sole process boundary and a fixed command;
- `EngineeringRun`: state transitions, artifact lineage, approvals and audit chain.

### 3. Controlled execution — 6 minutes

1. Start a `BROWNFIELD` run as `submitter`.
2. Show the repository map and requirements/impact/architecture/security evidence.
3. Show `AWAITING_CHANGE_APPROVAL`; try a wrong hash or self-approval.
4. Approve `planHash` as `approver`.
5. Compare timestamps for the three parallel roles.
6. Inspect changed files, diff, artifact hashes and fixed build command.
7. Inspect parsed tests and `reportHashes`; explain why model prose cannot pass the gate.
8. Show `AWAITING_RELEASE_APPROVAL`, then approve the exact `outcomeHash`.

Key line: “Release approval records readiness; no code path deploys production.”

### 4. Non-linear and failure behavior — 4 minutes

- Use a `repair-demo` requirement: first compile fails, the repair receives build output plus diff and
  current file hash, second validation passes, and retry/recovery metrics update.
- Use an ordinary unrecoverable injected failure: the immutable baseline is restored and manifests
  match.
- Start `AMBIGUOUS`: it stops before patching. Submit a clarified `/changes` request and show the new
  revision/plan and invalidated prior evidence.

### 5. Evidence, security and durability — 2 minutes

Show authenticated principals on approvals, the previous/event-hash audit chain, provider/model/
prompt metadata, PostgreSQL Compose configuration, restart-persistence test, safe output/path bounds
and CI gates.

### 6. Honest close — 2 minutes

The runnable default is a clearly labeled deterministic model test double; a real OpenAI Responses
adapter is configured with environment variables. Production still needs organization OIDC,
distributed leases, sandboxed workers, signed audit export and deployment integration. These are
deliberately outside an agent's authority, not silently claimed.

## Decisions to defend

### Why a state machine and dependency graph?

It makes entry/exit conditions, parallel branches, synchronization, retries, rollback and invalidation
inspectable. A prompt cannot alter control flow or skip a gate.

### Why typed operations instead of generated shell commands?

Repository content and model output are untrusted. Typed create/update/delete operations allow path,
size, secret, protected-file and optimistic-hash checks. The only process capability selects a fixed
Maven/Gradle command.

### Why two hashes and two approvals?

The plan hash prevents approving one scope and executing another. The outcome hash binds release
approval to the actual current patch, build/reports and reviews. Both invalidate when upstream state
changes; separation of duties prevents the submitter approving their own run.

### Is the LLM really used?

Yes when `AGENTIC_MODEL_PROVIDER=openai`; `OpenAiLlmClient` calls the Responses API with a strict
JSON schema and captures usage/status evidence. CI deliberately uses `DeterministicLlmClient` so it
is repeatable and carries no secret. Both pass through the identical gateway and downstream controls.

### How is testing real?

The test-generation agent proposes source, but cannot mark it passed. `FixedBuildExecutor` runs an
allow-listed process with a timeout and scrubbed environment. The gate checks exit status, parsed
counts and hashes of actual XML reports.

### How is rollback real?

Before change, the service creates an immutable baseline copy and records its manifest. On failure
or cancellation it replaces the working copy and recomputes the manifest. Release is never marked
ready if restoration or evidence validation fails.

### What makes brownfield reasoning non-canned?

The analyzer reads the selected snapshot and produces file/source/test/package/type/method,
dependency and relevant-snippet evidence with hashes. A test creates different repositories and
proves different maps. Those maps enter prompts and plan lineage.

### How would this scale horizontally?

PostgreSQL becomes the authoritative run store with a version and lease owner/expiry. Workers claim
ready graph nodes transactionally, heartbeat, make tools idempotent and reject stale completions.
Workspaces move to ephemeral sandbox jobs; audit events export to immutable storage. The domain
state machine and evidence contracts remain unchanged.

## Likely limitations question

Say plainly: “This is a production-minded prototype, not a deployment platform. Local Basic auth is
for the demo; the PostgreSQL adapter persists state but does not yet provide distributed worker
leases; audit hashes are not externally signed; and external-model quality needs environment-specific
evaluation. I made those limitations visible and fail closed around them.”

## Closing summary

The strongest evidence is the execution record itself: repository-derived context, provider metadata,
overlapping branch times, actual patch/diff, fixed process result, test-report hashes, bounded repair,
exact rollback, authenticated hash approvals and durable audit lineage. That is the distinction
between a workflow diagram and controlled agentic software engineering.
