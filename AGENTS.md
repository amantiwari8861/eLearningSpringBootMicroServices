# Working in the LMS repository

Start with [README.md](README.md) and the [audit and roadmap](docs/audit.md). This is a Java 17 Maven reactor with seven Spring Boot applications. Its executable PostgreSQL design is further along than its Java business logic.

## Scope and permission

- For a question, design, or review, inspect the working tree and present the result. Get the user's permission before applying proposed changes. A request to create or update named files authorizes those edits.
- During a repository audit, preserve application behavior. Record proposed business features, configuration changes, dependency upgrades and infrastructure work in the roadmap for the user's decision.
- Check `git status --short --branch --untracked-files=all` first. Read the working POMs and untracked modules. The index can describe a different project from the one on disk. Preserve existing staged work, untracked designs, and unrelated user files.

## Find the source of a claim

- Read the [reactor POM](pom.xml), the affected module's POM, its `src/main/java` sources, its `src/main/resources/application.properties` and its tests. Six modules inherit the reactor POM. `eureka-server` declares its own Spring Boot parent.
- Check both ends of an API change. The gateway defines explicit path predicates, and the receiving service must supply the handler. A route prefix alone establishes neither a business endpoint nor its response schema.
- Keep service code, packaged framework capabilities, and the [standalone database design](docs/database.md) distinct. Verify a dependency and a working endpoint before documenting a security or monitoring capability.
- The SQL in `docs/lms-platform.sql` provisions seven databases and cluster roles. Run it only for a requested provisioning task on the intended target. It is independent of normal Maven builds and application startup.

## Change and verify

- Use the root Maven wrapper. From PowerShell, `.\mvnw.cmd -pl api-gateway -am test` runs the gateway tests and reactor prerequisites. For a repository-wide change run `.\mvnw.cmd -fae verify`. On Unix use `sh ./mvnw`. Start a single service with `.\mvnw.cmd -f api-gateway/pom.xml spring-boot:run`.
- Configure local dependencies before starting applications or running the full suite. Auth, course and order use PostgreSQL with Hibernate schema updates enabled. Point datasource settings at a disposable database for tests. Override `MONGODB_URI` with a local address for course-service; its configured fallback includes credentials for an external host. See [local setup](docs/development.md).
- Keep credentials, provider URIs with user information, generated security passwords, and unrelated personal records out of documentation, commits, command output and examples.
- Test through the entry point affected by the change. A passing `contextLoads` test demonstrates context initialization. Route changes also need an HTTP check with a registered upstream. Data changes need a test of the relevant constraint or migration.
- Report the command, environment overrides, actual result and any unverified dependency. For a failed reactor, distinguish failed modules from skipped or unexecuted modules.

## Keep the docs useful

- Update [architecture and API behavior](docs/architecture.md) when routes or controllers change, [development](docs/development.md) when setup changes, and [database inventory](docs/database.md) when application persistence changes.
- Keep observed facts and proposed work visibly separate. Date new audit baselines. Put consequential design choices in `docs/adr/`, with their status, evidence and trade-offs. Record historical rationale only when a source establishes it.
- For a fresh initialization audit, inventory tracked and untracked source, existing docs, module versions, configuration, tests, infrastructure and delivery tooling before writing. Summarize verified features, risks, build results, changed files and decisions requiring approval. Mark roadmap items Completed, In Progress, Planned or Blocked according to observed work.
