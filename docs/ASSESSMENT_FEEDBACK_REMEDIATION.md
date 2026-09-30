# Assessment feedback remediation

The external feedback was substantially genuine: the earlier graph simulation demonstrated
orchestration concepts, but its template agents did not inspect a repository, invoke a real LLM,
produce a patch, execute tests or persist execution across restarts. The `/api/v1/engineering`
system below closes those gaps. The earlier `/api/v1/orchestration` API remains only as a compact
graph-model compatibility example and is not the evidence-producing assessment path.

| Feedback concern | Implemented remediation | Verifiable evidence |
|---|---|---|
| Template agents / no real provider | Provider-neutral `LlmClient`, real OpenAI Responses adapter and strict JSON-schema output; deterministic implementation is explicitly a test double | `OpenAiLlmClient`, `LlmConfiguration`, `StructuredAgentGateway`, `ModelSafetyTest` |
| No repository analysis | Snapshot-aware file/source/test/package/type/method/dependency map with relevant snippets and SHA-256 hashes | `RepositoryAnalyzer`, `RepositorySafetyTest.dynamicRepositoryContentProducesDifferentMaps` |
| No isolated checkout | Separate immutable baseline and writable workspace for a working tree or validated Git revision | `RepositoryWorkspaceService` |
| No code change/diff | Typed file operations, optimistic hashes, protected paths, secret scan, atomic writes, unified diff and artifact hashes | `GovernedPatchApplier`, `RepositorySafetyTest` |
| Tests were claimed rather than run | Only a fixed Maven `clean verify` or Gradle `clean test` process can pass; output, exit code, parsed counts and XML report hashes are stored | `FixedBuildExecutor`, `BuildEvidence`, validation gate |
| No repair loop | Failure output and current diff go to the repair role; repository hashes are refreshed; patch and build repeat within a bounded budget | `EngineeringExecutionServiceTest.repairsOnceFromFailureEvidenceAndThenPasses` |
| Evidence gates were synthetic | Plan approval binds `planHash`; quality requires current manifest, successful process, tests and reports; release approval binds `outcomeHash` | `EngineeringExecutionService`, exact-hash tests |
| Replan was not meaningful | Requirement change creates a new revision, restores baseline, rebuilds context and revision-tags historical approvals so they cannot authorize new work | `replanningInvalidatesPriorPlanAndRebuildsTheExecutionContext` |
| Approval identity could be spoofed | Spring Security identity and roles drive authorization; actor/approver fields were removed; self-approval is forbidden | `SecurityConfig`, controllers, `EngineeringSecurityTest` |
| State disappeared on restart | Atomic JSON run store and PostgreSQL run adapter; Compose uses PostgreSQL with a named volume | `FileEngineeringRunStore`, `PostgresEngineeringRunStore`, persistence restart test, `compose.yaml` |
| Rollback was only a label | Workspace is replaced from its immutable baseline and the restored manifest must match the original | `RepositoryWorkspaceService.rollback`, rollback test |
| Parallelism was not evidenced | Implementation, test and documentation roles execute on a bounded pool; each has start/end/latency evidence; the join has a timeout | `invokeParallel`, `AgentInvocationEvidence` |
| Unsafe model/tool boundary | Context redaction before persistence or model use, injection marking, strict output parsing, path confinement, no shell access in model/patch packages, fixed command allow-list | `ContextSanitizer`, `GovernedPatchApplier`, `ArchitectureBoundaryTest`, blocked-secret test |
| Audit data existed but was not self-verifying | Every run view recomputes sequence, previous-hash and event-digest integrity and exposes `auditChainValid` | `EngineeringRun.auditChainValid`, persistence restart test |
| Metrics were process-local claims | Durable run records drive success, retry, rollback, recovery and latency aggregation | `/api/v1/engineering/metrics`, `EngineeringReliabilityMetrics` |
| Weak quality gate | Maven verify runs tests, JaCoCo line/branch thresholds and SpotBugs; CI retains reports and reviews vulnerable dependency changes | `pom.xml`, `.github/workflows/ci.yml` |
| Scenarios lacked actual artifacts | Greenfield, brownfield/repair and ambiguous test cases assert repository maps, patches, reports, gates and terminal states | `EngineeringExecutionServiceTest`, `SCENARIOS.md` |

## What is deliberately not claimed

- CI does not call OpenAI because assessment builds should not require or expose a secret. Set
  `AGENTIC_MODEL_PROVIDER=openai` and `OPENAI_API_KEY` to exercise the real adapter.
- `RELEASE_READY` is evidence readiness, not deployment. Agents have no deployment credential or
  arbitrary command interface.
- The local Basic-auth users are demonstration identities. Production requires organization OIDC,
  group/role mapping, credential rotation and signed approval claims.
- PostgreSQL makes run state durable, but this prototype does not yet implement distributed worker
  leases. Use one orchestrator instance or add versioned lease claims before horizontal execution.
- The audit chain is tamper-evident inside the store, not externally immutable. Production should
  sign and export it to controlled append-only retention.

## Clean-room comparison note

The public reference repository named in the assignment follow-up was reviewed for high-level
patterns only. This implementation independently applies the useful ideas—provider abstraction,
isolated workspace, patch governance, evidence lineage and fixed validation—within this repository's
existing Spring architecture. No source was copied.
