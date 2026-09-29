# Agentic URL Shortener

A production-minded Java prototype that combines a secure URL shortener with a governed,
stateful SDLC orchestration engine. The repository is intentionally structured as an interview
artifact: runnable code, explicit trade-offs, observable control flow, tests, and decision records.

> The implementation is built incrementally across seven meaningful commits. See the completed
> architecture and walkthrough documentation in `docs/`.

## Quick start

Prerequisite: JDK 21.

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Then open `http://localhost:8080/swagger-ui.html`.

