# Required execution scenarios and evidence

These scenarios execute through `/api/v1/engineering/runs`. The tests use the deterministic LLM
test double but exercise the production repository analysis, workspace, patch, build-evidence,
approval, audit, retry and rollback paths. Selecting the OpenAI provider changes only the model
adapter.

## 1. Greenfield — add a new capability

Requirement: “Create an expiry capability with executable tests and reviewer documentation.”

The engine snapshots the repository, creates a map, invokes requirements/architecture/security,
and calculates a `planHash`. It cannot mutate files until a different authenticated identity
approves that exact hash. Implementation, test and documentation proposals then run concurrently;
the patch creates a requirement-specific production type, executable JUnit test and reviewer file.
Validation requires a passing fixed build and test-report hashes. The outcome then waits for an
independent exact-`outcomeHash` release approval.

Executable proof: `EngineeringExecutionServiceTest.executesRequirementSpecificPatchAndExactHashApprovalChain`.
It asserts production/test/docs files, build evidence, quality audit events, both approval gates and
the `RELEASE_READY` transition.

## 2. Brownfield — analyze, fail, repair and revalidate

Requirement: “Create a repair-demo capability with executable tests and documentation.”

Brownfield planning adds impact analysis based on discovered files, symbols, dependencies, snippets
and hashes. The deterministic test scenario deliberately proposes a Java compilation defect. The
first build returns failure evidence; the orchestrator refreshes the repository map and invokes the
repair role with that output and the current diff. Repair supplies an update operation bound to the
failed file's current SHA-256. A second build passes with a hashed test report, after which security
and release-readiness review run and the quality gate opens.

Executable proof: `EngineeringExecutionServiceTest.repairsOnceFromFailureEvidenceAndThenPasses`.
It asserts two build attempts, a recorded repair invocation, a passing validation and retry metric.
`failedBuildWithoutSafeRepairRestoresExactBaseline` proves the alternate path: no valid repair means
`ROLLED_BACK`, and the restored manifest equals the baseline.

Repository-specific reasoning is separately proven by
`RepositorySafetyTest.dynamicRepositoryContentProducesDifferentMaps`, which builds two distinct
temporary repositories and asserts distinct maps/hashes rather than canned output.

## 3. Ambiguous — stop before mutation

Requirement: “Improve the service with whatever is best while preserving safe controls.”

The requirement agent flags unresolved scope and the scenario itself requires clarification. The
run stops at `AWAITING_CLARIFICATION` with a plan hash and no patch. A submitter can use `/changes`
to provide a concrete requirement and reason. That action increments the revision, records lineage,
recreates the workspace and plan, and ensures no prior approval can authorize the new output.

Executable proof: `EngineeringExecutionServiceTest.ambiguousRequirementStopsAtClarificationGate`
asserts the stop and absence of mutation.
`replanningInvalidatesPriorPlanAndRebuildsTheExecutionContext` asserts the new revision/hash, cleared
current patch, absence of an approval for that revision, and `RUN_REPLANNED` audit event.

## Cross-scenario negative evidence

| Risk | Executable evidence |
|---|---|
| Stale or self approval | `rejectsStaleHashAndSelfApproval` |
| Anonymous execution / wrong role | `EngineeringSecurityTest` |
| Path traversal, protected file or secret generation | `RepositorySafetyTest` |
| Prompt injection or secret leakage to provider | `ModelSafetyTest` |
| Invalid structured model response | `ModelSafetyTest.invalidModelJsonStopsBeforePatchApplication` |
| Process execution from an untrusted layer | `ArchitectureBoundaryTest` |
| Restart state loss | `EngineeringRunPersistenceTest` |

## What to inspect in an API response

For a concrete review, show `repositoryMap.relevantFiles/symbols`, `agentInvocations` provider/model
metadata, `patch.unifiedDiff/artifactHashes`, `validationAttempts.command/exitCode/reportHashes`,
`validation.failures`, approval principals/hashes, the `auditTrail` previous/event hash chain and
the recomputed `auditChainValid` result.
Those fields distinguish real execution evidence from a stage label or generated narrative.
