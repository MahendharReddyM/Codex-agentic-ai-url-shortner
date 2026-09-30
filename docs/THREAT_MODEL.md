# Threat model

## Assets and trust boundaries

Assets include redirect integrity, destination confidentiality, service availability, analytics
privacy, workflow decisions, approval identity, generated artifacts, and the audit trail. Untrusted
inputs enter at every HTTP endpoint. The network, future persistence adapters, agent outputs, and
human approval clients are separate trust boundaries.

## Principal threats

| Threat | Prototype mitigation | Production follow-up |
|---|---|---|
| SSRF / internal target discovery | HTTP(S) only; host required; user info, localhost, and private/local IP literals rejected | Resolve through a controlled egress proxy; verify every DNS answer and redirect hop |
| Phishing/open redirect abuse | alias validation, disable endpoint, rate limits, inspectable metadata | authentication, reputation scanning, abuse reporting, domain allow/deny policy |
| Alias/code collision | atomic reservation; secure random Base62; bounded retry | database unique constraint and collision metric |
| Replay/duplicate create | request fingerprint tied to bounded idempotency key | durable idempotency table with expiry |
| Client tracking/privacy | daily keyed pseudonym; no raw IP; referrer reduced to host; bounded retention | managed secret rotation, consent/retention policy, deletion workflow |
| Header/log injection | correlation ID safe pattern and length | structured log pipeline and central redaction |
| Denial of service | input bounds, redirect rate limit, retention caps, bounded agent retries | gateway limits, distributed token bucket, autoscaling and quotas |
| Prompt/control injection | secret/injection sanitization, strict output schema, typed patch operations, no model shell and no autonomous deploy | pin evaluated model/prompt versions; sandbox workers and allowlist egress |
| Repository traversal/symlink escape | approved root, normalized relative paths, symlink rejection, protected paths and isolated workspaces | ephemeral container/filesystem sandbox and mandatory access controls |
| Generated secret or oversized change | generated-content secret scan plus file/count/byte limits | organization DLP and secret-scanning service |
| Arbitrary command execution | model and patch layers cannot spawn processes; validator selects fixed Maven/Gradle arguments | locked-down build image, dependency proxy and no default network |
| Approval spoofing | authenticated principal, role checks, separation of duties, exact plan/outcome hashes; no body-supplied identity | OIDC identity, managed RBAC and signed approval claim |
| Audit tampering | SHA-256 event hash chain and verification endpoint | signed events in append-only/WORM store with independent monitoring |
| Destructive change | policy safe-stop, typed bounded patch, exact rollback and mandatory change/release gates | change ticket integration and database migration safety tooling |
| Run-state loss | atomic JSON store or PostgreSQL adapter | managed backups, migrations, multi-AZ database and restore exercises |

## Security assumptions

- TLS terminates at a trusted proxy in production.
- Proxy configuration supplies a trustworthy client address; direct public access is disabled.
- `ANALYTICS_SALT` is supplied from a secret manager and rotated under a retention-aware policy.
- Repository implementations enforce tenant ownership and authorization before exposing data.
- Agents operate with least-privilege, short-lived credentials and no default production access.

## Disclosure

Do not put security-sensitive details in a public issue. For a real deployment, publish an owned
security contact and response SLA before accepting external traffic.

