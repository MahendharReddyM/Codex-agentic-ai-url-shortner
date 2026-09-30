# Repository file guide and use cases

This is the reviewer map. Paths under `src/main/java/dev/assessment/urlshortener` and the matching
test root are shortened below. Generated `target`, `data` and `work` content is intentionally not
source-controlled.

## Build, runtime and delivery

| File | Use case |
|---|---|
| `pom.xml` | Java 21/Spring dependencies; Surefire, JaCoCo line/branch gates and SpotBugs verification |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*` | Reproducible Maven execution without a preinstalled Maven |
| `Dockerfile` | Multi-stage application image build and non-root runtime |
| `compose.yaml` | App plus durable PostgreSQL, health dependency, read-only repository mount and isolated work volume |
| `.github/workflows/ci.yml` | Verify, retain reports/coverage/static evidence and dependency-review pull requests |
| `src/main/resources/application.yml` | URL, reliability, model, repository, workspace, persistence, timeout, limit and demo-identity settings |
| `demo/assessment.http` | Repeatable product and engineering-chain API demonstration |
| `.gitignore`, `.dockerignore`, `.editorconfig` | Source hygiene, small build context and consistent formatting |
| `LICENSE` | Repository license |

## Application and shared platform

| File | Use case |
|---|---|
| `AgenticUrlShortenerApplication.java` | Boot entry point and typed configuration registration |
| `config/OpenApiConfig.java` | API title/version metadata |
| `config/SecurityConfig.java` | Stateless HTTP Basic demo auth, roles and method security |
| `config/ReliabilityProperties.java` | Validated URL-service capacity/privacy settings |
| `config/TimeConfig.java` | Injectable clock |
| `config/UrlShortenerProperties.java` | Public base URL and code-length settings |
| `shared/ApiError.java` | Stable API error body |
| `shared/DomainException.java` | HTTP-aware domain failure |
| `shared/GlobalExceptionHandler.java` | Validation/domain/authorization/unexpected error mapping |
| `shared/CorrelationIdFilter.java` | Safe correlation-ID propagation/generation |

## Primary engineering execution system

| File | Use case |
|---|---|
| `engineering/EngineeringController.java` | Authenticated start/read/approve/replan/cancel/metrics endpoints |
| `engineering/EngineeringExecutionService.java` | Stateful graph execution, parallel join, gates, retry/repair, rollback, replan and metrics |
| `engineering/EngineeringRun.java` | Durable aggregate, legal transitions and hash-chained audit lineage |
| `engineering/EngineeringModels.java` | Typed requests, statuses, operations, agent/build/patch/approval/validation/rollback evidence and API views |
| `engineering/EngineeringRunStore.java` | Persistence port |
| `engineering/FileEngineeringRunStore.java` | Atomic restart-durable JSON store for a zero-infrastructure run |
| `engineering/PostgresEngineeringRunStore.java` | Durable PostgreSQL table creation, upsert and reload adapter |
| `engineering/config/AgenticExecutionProperties.java` | Model, repository, workspace, persistence, timeout and resource-bound configuration |
| `engineering/analysis/RepositoryAnalyzer.java` | Build/files/source/tests/packages/types/methods/dependencies/snippets/hash map |
| `engineering/workspace/RepositoryWorkspaceService.java` | Safe working-tree or Git-revision export, baseline/workspace isolation, manifests and exact restore |
| `engineering/patch/GovernedPatchApplier.java` | Typed/atomic file changes, protected paths, optimistic hashes, secret/size limits, diff and artifact hashes |
| `engineering/validation/BuildRunner.java` | Deterministic validation capability port |
| `engineering/validation/FixedBuildExecutor.java` | Allow-listed Maven/Gradle process, environment scrub, timeout/output limit, test parsing/report hashing |

## Model boundary

| File | Use case |
|---|---|
| `engineering/model/LlmClient.java` | Provider-neutral prompt/response contract |
| `engineering/model/LlmConfiguration.java` | Explicit OpenAI or deterministic provider selection |
| `engineering/model/OpenAiLlmClient.java` | Real Responses API call with JSON schema, timeout, usage and status extraction |
| `engineering/model/DeterministicLlmClient.java` | Offline/test double producing requirement-specific typed operations, including controlled repair demo |
| `engineering/model/StructuredAgentGateway.java` | Context construction, strict JSON parsing/semantic bounds and invocation evidence hashes |
| `engineering/model/ContextSanitizer.java` | Secret redaction, prompt-injection marking and context length enforcement |

## Governance

| File | Use case |
|---|---|
| `governance/PolicyEngine.java` | Policy entry-gate port |
| `governance/DefaultPolicyEngine.java` | Blocks secrets, destructive intent and control bypass; flags ambiguity |
| `governance/PolicyDecision.java` | Aggregate allow/block outcome |
| `governance/PolicyFinding.java` | Coded finding evidence |
| `governance/PolicySeverity.java` | Warning/blocking classification |

## URL-shortener product

| File/group | Use case |
|---|---|
| `link/ShortLink.java` | Link aggregate and active/expired behavior |
| `link/CreateShortLinkRequest.java`, `ShortLinkResponse.java` | Validated public request/response contracts |
| `link/ShortLinkController.java` | Create, inspect, redirect and disable HTTP endpoints |
| `link/ShortLinkService.java` | Lifecycle, collision retry, idempotency, normalization and resolution rules |
| `link/ShortLinkRepository.java`, `InMemoryShortLinkRepository.java` | Persistence port and atomic lock-striped demo adapter |
| `link/CodeGenerator.java`, `SecureBase62CodeGenerator.java` | Code-generation port and secure Base62 implementation |
| `link/UrlSafetyPolicy.java` | HTTP(S), host, user-info and private/local-literal checks |
| `analytics/ClickEvent.java`, `LinkAnalyticsResponse.java` | Internal event and public aggregation models |
| `analytics/AnalyticsController.java`, `AnalyticsService.java` | Analytics query plus bounded non-blocking event admission/writing |
| `analytics/AnalyticsRepository.java`, `InMemoryAnalyticsRepository.java` | Analytics port and bounded demo retention |
| `analytics/PrivacyHasher.java` | Daily HMAC client pseudonym and reduced referrer classification |
| `reliability/RedirectRateLimiter.java`, `InMemoryFixedWindowRateLimiter.java` | Limiter port and bounded fixed-window adapter |

## Legacy graph compatibility package

`orchestration/*` is the earlier, non-mutating graph visualization API retained for demonstrating
explicit DAG validation and backward compatibility. `WorkflowGraph*`, `Stage*`, `WorkflowRun*`,
`OrchestrationService/Controller`, audit and metrics types model graph state. `StageAgent`,
`AgentTask/Output/Registry` and the `*Agent` classes create advisory artifacts only. They deliberately
do not claim executable test success; use `/api/v1/engineering` for assessment evidence.

## Tests

| File | Use case |
|---|---|
| `AgenticUrlShortenerApplicationTests.java` | Full Spring context smoke test |
| `ApiIntegrationTest.java` | URL APIs, redirect, eventual analytics, deletion, errors and correlation IDs |
| `analytics/AnalyticsServiceTest.java` | Privacy aggregation, async behavior and bounded backpressure |
| `link/ShortLinkServiceTest.java` | URL/idempotency/expiry/disable behavior |
| `link/UrlSafetyPolicyTest.java` | Public URL acceptance and unsafe destination rejection |
| `link/InMemoryShortLinkRepositoryConcurrencyTest.java` | Duplicate exclusion and independent-write concurrency |
| `reliability/InMemoryFixedWindowRateLimiterTest.java` | Window limit and reset |
| `orchestration/WorkflowGraphTest.java` | Cycle rejection and downstream impact for the compatibility graph |
| `orchestration/OrchestrationServiceIntegrationTest.java` | Compatibility graph approvals, branches, audit, rollback and metrics |
| `engineering/EngineeringExecutionServiceTest.java` | Plan/outcome hashes, patch chain, parallel work, build evidence, repair, rollback, ambiguity and replan |
| `engineering/EngineeringSecurityTest.java` | Authentication and role authorization |
| `engineering/RepositorySafetyTest.java` | Dynamic repository reasoning and mutation guardrails |
| `engineering/ModelSafetyTest.java` | Secret/injection sanitization and invalid model output |
| `engineering/EngineeringRunPersistenceTest.java` | Durable reload across store instances |
| `engineering/ArchitectureBoundaryTest.java` | Process/model dependency boundaries |
| `engineering/EngineeringTestProperties.java` | Small deterministic bounds for test fixtures |

## Reviewer documentation

| File | Use case |
|---|---|
| `README.md` | Entry point, setup, execution chain, APIs and prototype boundary |
| `docs/ARCHITECTURE.md` | Components, dependency graph, gates, evidence and scaling path |
| `docs/ASSESSMENT_FEEDBACK_REMEDIATION.md` | Feedback-to-code/test traceability matrix |
| `docs/SCENARIOS.md` | Greenfield, brownfield/repair and ambiguous executable evidence |
| `docs/TESTING.md` | Quality gate, test layers, failure injections and release checklist |
| `docs/THREAT_MODEL.md` | Assets, trust boundaries, controls and residual risk |
| `docs/INTERVIEW_WALKTHROUGH.md` | Demonstration script and design-defense answers |
| `docs/decisions/0001-explicit-dag.md` | Why dependency/state semantics are explicit |
| `docs/decisions/0002-ports-and-in-memory-adapters.md` | Why zero-infrastructure product/compatibility adapters remain behind ports |

## Two earlier URL-service scale improvements

1. `AnalyticsService` moved secondary writes off the redirect path into a bounded pool/queue with
   accepted/written/dropped/depth metrics, preventing slow analytics from consuming redirect threads
   or unbounded memory.
2. `InMemoryShortLinkRepository` replaced one global creation monitor with ordered lock striping,
   allowing unrelated writes to proceed concurrently while preserving code/idempotency uniqueness.

The engineering upgrade adds a separate scale path: durable PostgreSQL run storage and clean ports
for external model providers, workspaces and validators. Multi-instance execution still requires
versioned worker leases, documented in the architecture limitations.
