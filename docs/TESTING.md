# Testing, validation and release readiness

## Authoritative command

```bash
./mvnw --batch-mode verify
```

`verify` executes JUnit, creates JaCoCo coverage, enforces 70% bundle line and 50% branch thresholds, and
runs high-confidence SpotBugs analysis. CI runs the same command on Java 21, retains Surefire,
coverage and static-analysis artifacts, and blocks pull requests that add high-severity vulnerable
dependencies.

## Test layers

| Layer | Representative behavior |
|---|---|
| URL domain | normalization, unsafe destinations, expiry, disable, idempotency and alias conflicts |
| Reliability | rate-window reset, privacy reduction, bounded asynchronous analytics and backpressure |
| Concurrency | duplicate exclusion and parallel independent link creation |
| HTTP | create/redirect/analytics/delete, errors, correlation IDs and role authorization |
| Repository safety | dynamic maps, traversal/symlink/protected path rejection, byte/file limits and secret rejection |
| Model boundary | context redaction, prompt-injection marking and invalid-schema safe stop |
| Execution integration | exact-hash approvals, parallel branches, patch, test-report evidence, repair, rollback, replan, ambiguity and metrics |
| Persistence | reload an engineering aggregate and its hash-chain evidence from a new store instance |
| Architecture | model/patch layers cannot spawn processes; validation cannot depend on an LLM |
| Application | complete Spring context wiring |

## The quality gate

An agent's statement that tests passed has no authority. `ValidationEvidence.passed` requires all of
the following for the current revision:

1. a governed patch was actually applied;
2. the fixed Maven/Gradle process exited successfully;
3. at least one test was parsed from process output;
4. no parsed tests failed;
5. one or more Surefire/Gradle XML reports were found and SHA-256 hashed;
6. post-change security review completed without a blocking risk;
7. reviewer documentation is in the patch; and
8. the live workspace manifest equals the recorded changed manifest.

The `outcomeHash` incorporates the plan, changed manifest, build output, test-report hashes, security
and release agent outputs and patch artifact hashes. A stale approval therefore fails closed.

## Failure injection and recovery

| Injection | Expected behavior | Proof |
|---|---|---|
| Deliberate generated compiler defect | first build fails, repair uses failure/diff/hash evidence, second passes | `repairsOnceFromFailureEvidenceAndThenPasses` |
| Failure without a safe repair | retry budget ends and exact baseline is restored | `failedBuildWithoutSafeRepairRestoresExactBaseline` |
| Invalid model JSON | no patch and controlled exception/safe stop | `ModelSafetyTest` |
| Traversal/protected/secret operation | rejected before mutation | `RepositorySafetyTest` |
| Stale plan hash or submitter self-approval | HTTP/domain rejection | exact-hash and security tests |
| Ambiguous intent | waits for clarification with no patch | ambiguous scenario test |
| Changed upstream requirement | new revision/plan; prior evidence stays historical and cannot authorize the revision | replanning test |

## Manual assessment walkthrough

- [ ] Start with `submitter`; confirm repository evidence and `AWAITING_CHANGE_APPROVAL`.
- [ ] Try a stale hash and self-approval; confirm rejection.
- [ ] Approve the exact plan as `approver`.
- [ ] Inspect overlapping implementation/test/documentation invocation timestamps.
- [ ] Inspect diff, artifact hashes, fixed command, exit code, tests and XML report hashes.
- [ ] Confirm the quality gate opens only on passing current evidence.
- [ ] Try a stale release hash; then approve the exact outcome hash.
- [ ] Start an ambiguous run; confirm no source mutation.
- [ ] Replan and show revision increment and invalidated approval lineage.
- [ ] Trigger an unrecoverable validation failure and show restored baseline manifest.
- [ ] Restart the service (file or PostgreSQL mode) and retrieve the run.
- [ ] Inspect `/api/v1/engineering/metrics` and the audit hash chain.

## Limitations and additional production validation

CI uses a deterministic model test double, so it validates the control plane without paying for or
exposing a provider key. A pre-production environment should additionally run contract/evaluation
suites against the configured model, score patch correctness and adversarial prompt cases, and pin
approved model/prompt versions.

Production rollout also needs PostgreSQL migration/backup/restore tests, distributed worker lease
and crash-recovery tests, container sandbox/egress tests, OIDC/RBAC integration, signed immutable
audit export, load/soak testing, SLO alerts, dependency/container scanning and canary rollback. A
human remains responsible for the final code review and deployment decision.
