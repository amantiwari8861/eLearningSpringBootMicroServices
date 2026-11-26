# Repository audit and roadmap

**Baseline date** 2026-11-25. **Scope** The local working tree, including untracked modules and design documents. At the start of the audit Git reported no commits on `master`, six staged root files, and further unstaged edits to the root POM. The implementation inventory describes the files on disk. No historical rationale or previously deployed version was available from this Git history.

## What exists

- **Build and runtime.** Seven Spring Boot applications build in one Maven reactor. Eureka provides discovery. The WebFlux gateway forwards four explicit prefixes to auth, course, order and AI. Notification registers independently. Each application contains one production Java bootstrap class and one context-load test. [Architecture and API inventory](architecture.md) · [Stack and setup](development.md)
- **LMS features.** Auth has framework security, course and order have persistence starters, and the gateway can reach registered services. There are no application-defined login, course, checkout, playback, notification or AI handlers. There are no application event producers or consumers. The SQL design models these future workflows in substantially more detail than the Java sources. [Database inventory](database.md)
- **Documentation and tooling.** The initial working tree contained a PostgreSQL bootstrap, its attribute guide, two design SVGs and seven ignored Spring Initializr `HELP.md` files. The wrappers and POMs are the build entry points. The inventory found no Dockerfile, Compose stack, Kubernetes manifests, AWS infrastructure definitions or CI/CD workflows. No deployment, backup service, or external monitoring installation was verified.

## Build and test evidence

| Item | Observed result |
| --- | --- |
| Host and tools | Windows 11, Oracle JDK 25.0.2, root wrapper Maven 3.10.1, Java compilation target 17 |
| Command | `.\mvnw.cmd -o -B -ntp -fae verify` on a fresh temporary copy of the root wrapper, POMs and all module sources, using the populated local Maven cache |
| Reactor | Root POM and all seven application modules succeeded in 1 min 35 s. Maven compiled, tested and packaged all seven service JARs. |
| Tests | **7 run, 7 passed, 0 failures, 0 errors, 0 skipped**. One Surefire report per module, each for its `@SpringBootTest` context startup. |
| Dependencies used for the test run | A fresh password-protected PostgreSQL 18.4 cluster on loopback. The three JPA contexts used a shared, empty disposable database. The course MongoDB URI pointed at an unused loopback port. Eureka client registration and fetching were disabled for the Maven test run. |
| Coverage limit | The suite has no assertions about business behavior, service-to-service authorization, or data writes. A passing course context did not require a MongoDB server. Java 17 execution, successful MongoDB operations and a full external deployment remain unverified. |

## HTTP behavior observed on packaged applications

The smoke check launched the fresh JARs on loopback using temporary ports. Eureka registration and discovery were enabled for the clients. Shorter discovery refresh intervals and disabled discovery caches reduced startup waits. Requests were unauthenticated `GET`s with `Accept: application/json`, except the dashboard request used `text/html`. The `example` suffixes below deliberately exercise paths that have no application handler.

| Request | Context | Actual response |
| --- | --- | --- |
| Registry `/` and `/eureka/apps` | Registry running | **200** HTML dashboard and **200** JSON with top-level `applications`, respectively |
| Registry `/actuator/health`, `/actuator/info`, `/actuator/metrics` | Registry running | **200** for all three. Health contained `status: UP` and `groups`. Info returned an empty JSON object. Metrics contained a `names` array. |
| Registry `/actuator/prometheus` | Registry running | **404** JSON. The registry includes Actuator and lacks the Prometheus meter registry. |
| Gateway `/api/v1/orders/example` | Order service had not started | **503** JSON containing `timestamp`, `path`, `status`, `error` and `requestId` |
| Gateway `/api/v1/notifications/example`, `/actuator/health` | Gateway running | **404** gateway JSON with `requestId`. Notification has no route and the gateway has no packaged Actuator. |
| Direct and gateway `/api/v1/auth/example` | Auth available | **401** with a Basic authentication challenge. A direct auth `/actuator/health` request also received **401**. The security filter can challenge a request before a handler is selected. |
| Direct and gateway `/api/v1/ai/example`, `/api/v1/courses/example`, `/api/v1/orders/example` | Corresponding service available | **404** upstream JSON with `timestamp`, `status`, `error`, and `path`. Registering a service makes forwarding possible but does not create a business handler. |
| Direct notification `/api/v1/notifications/example`; direct `/actuator/health` on AI, course, order and notification | Corresponding service available | **404** upstream JSON |

## Security and technical debt observations

| Priority | Evidence in the working tree | Consequence |
| --- | --- | --- |
| P0 | The [course properties](../course-service/src/main/resources/application.properties) contain a credential-bearing external MongoDB fallback. PostgreSQL credentials appear as literals in the auth, course, order and AI properties. | Anyone receiving those files receives the values. A course startup can attempt the external connection when the URI is unset. Credential validity and prior disclosure were not tested. |
| P1 | [Auth](../auth-service/pom.xml) supplies default Spring Security. The [gateway](../api-gateway/pom.xml) and other backends define no token-validation or authorization policy. The registry dashboard and registry JSON were accessible without authentication in the local check. | The scaffold has no application policy for learner, instructor or organization access. Registry reachability depends on deployment networking. |
| P1 | Three JPA modules enable Hibernate schema updates and SQL display. There are no service migrations, mapped entities or test-specific datasource profiles. | Pointing a test at the wrong environment can open a real connection. Future entity changes could alter tables at startup. SQL output requires care as business data is added. |
| P1 | The seven tests only start application contexts. MongoDB unavailability did not fail the course test. | Green tests provide little evidence about business APIs, datastore behavior or authorization. |
| P2 | Every module requests management endpoints. Only Eureka packaged Actuator, and no service packaged a Prometheus registry. | Configured exposure names overstate the observable HTTP surface. There is no checked-in scrape or alert configuration. |
| P2 | AI has datasource/JPA settings without persistence dependencies. Course configures two stores; the proposed catalog uses PostgreSQL. Gateway repeats the Cloud BOM and Eureka manages its own Boot parent. | Data ownership is undecided. Configuration and dependency versions can drift as modules evolve. |

## Prioritized roadmap and approval decisions

The statuses below describe repository evidence as of the audit. Planned rows are proposals for the user's approval.

| Status | Priority | Work | Completion evidence or dependency |
| --- | --- | --- | --- |
| Completed | Baseline | Inventory the seven applications, document routes and data design, and establish the build/test baseline. | Passing reactor, test counts and HTTP observations above |
| In Progress | — | No additional implementation work was verified as active at audit close. | Assign an owner and select a scoped change when work begins. |
| Planned | P0 | Remove credential defaults, move configuration to environment/secret storage, and rotate any affected live credentials. | Owner approval and provider access for rotation. Verify a fresh startup targets only intended services and review publication history before sharing. |
| Planned | P1 | Choose an identity provider, gateway/resource-service authorization model, and access controls for management and discovery. | Approve an authorization design and acceptance tests for anonymous, authorized and cross-organization requests. |
| Planned | P1 | Decide the course datastore and adopt an initial service migration with separate runtime and migration roles. | Review [ADR 0001](adr/0001-service-data-ownership.md). Verify installation and upgrade on a disposable database. |
| Planned | P1 | Add repeatable isolated test dependencies, route/authorization and persistence tests, and a CI job that runs the wrapper's `verify` goal. | Approve tooling and dependencies. A clean checkout should reproduce the baseline without hosted services or local credentials. |
| Planned | P2 | Align management dependencies and monitoring with the endpoints operators actually require. Review unused AI settings and duplicated version declarations. | Approve the observability scope; demonstrate the intended endpoint, scrape and alert, with controlled access. |
| Blocked | Release readiness | Production rollout of paid LMS access. | Requires implemented and tested identity, payment, entitlement and content flows, credential remediation, and an agreed deployment and recovery process. |

## Implementation update (2026-11-26)

The statuses below supersede the baseline rows above only for the scoped changes listed here.

| Status | Item | Observed evidence |
| --- | --- | --- |
| Completed | Local databases recreated by the user | `auth_db`, `course_db` and `order_db` are present; `postgres` remains as the maintenance database. |
| Completed | Lombok works in Maven on JDK 25 | The root POM now sets `proc=full` and registers Lombok in `annotationProcessorPaths`. A `clean test` compiles `course-service`; `setCourse` and `setModule` are present in the generated classes. |
| Completed | Duplicate JPA mappings removed | `BaseEntity` is now the only owner of `id`, `createdAt`, `updatedAt` and `version`. `Course` extends it; `Category`, `CourseModule` and `Lesson` no longer redeclare `id`. Each course table has one `id`, `created_at`, `updated_at` and `version` column. |
| Completed | Credentials externalized | Datasource username/password are placeholders with local development fallbacks. The external credential-bearing MongoDB fallback was replaced with a local URI. |
| Completed | Auth identity API | `auth-service` now has repositories, DTOs, a MapStruct mapper, BCrypt registration, HTTP Basic authentication, `POST /api/v1/auth/login` and `GET /api/v1/auth/me`. `AuthApiIntegrationTest` verifies registration, duplicate-email handling, login with valid/invalid credentials and authenticated current-user lookup. |
| Completed | Gateway identity validation and hardening | The gateway validates Basic credentials with auth-service, exempts public registration and login from header injection, strips client-supplied identity headers and forwards `X-User-Id`, `X-User-Email` and `X-User-Roles` to non-auth routes. Auth lookup failures return `401`; downstream failures now propagate instead of being misreported as bad credentials. Unusable or disabled identities are rejected, Basic credentials are removed from public and non-auth routes but preserved on protected auth routes, and identity headers are no longer injected into auth-service requests. `AuthHeadersGatewayFilterTest` covers these cases. |
| Completed | Course catalogue API | `course-service` now has JPA repositories, MapStruct mappers, DTO validation, a catalogue service, public read endpoints, instructor/admin write endpoints and role checks from gateway headers. `CourseApiIntegrationTest` covers reads, authorization, nested curriculum creation, duplicate handling and lesson counts. |
| Completed | Enrolment and progress API | `order-service` now owns `LessonProgress`, exposes enrolment and progress endpoints, enforces trainee ownership, and calculates percentages from course-service lesson counts. `OrderApiIntegrationTest` covers authorization, enrolment, progress updates, completion and duplicate handling. |
| Planned | Schema ownership and migrations | `ddl-auto=update` remains unchanged by request. Flyway/Liquibase adoption is still an open design decision. |
| Completed | Live endpoint smoke test fixes | Exercised every collection request against a fresh gateway and the running services. `UserRepository.findByEmail` now eager-fetches roles so live HTTP Basic authentication no longer throws `LazyInitializationException`; a regression test runs without a test transaction. The gateway exempts `GET /api/v1/courses/**` from authentication while keeping course writes protected, and `api-gateway` now packages Actuator so the documented management endpoints exist. |
| Completed | Postman local API collection | `postman/` contains one collection and one environment. The collection groups 22 requests by gateway, auth users, course categories/courses/modules/lessons, order enrollments/lesson progress/course progress and Eureka; create requests capture returned IDs into environment variables. Password variables are empty. |
| Completed | Small LMS first increment | The account → catalogue → enrolment → progress flow is implemented and verified module by module. Payments, quizzes, certificates, notifications, AI and migrations remain deliberately out of scope. |
| Completed | Real catalogue dataset | `course-service/src/main/resources/catalog/learning-data.json` contains 50 unique records: 38 `COURSE` and 12 `BLOG`, all `PUBLISHED`, with the supplied working image URLs. `CatalogDataLoader` saves them into `course_db` by slug on startup and skips existing slugs. `Course` adds type, category/subcategory, duration, rating, students, tags, image, course URL and featured/generated fields; `CourseApiIntegrationTest` now checks for at least 50 records and the `autocadtraining` type. |
| Completed | Gateway browser CORS | A high-precedence `CorsWebFilter` answers `OPTIONS /api/v1/**` before Basic identity validation. Origins are explicit loopback ports `3000` and `5173` (overridable with `LMS_CORS_ALLOWED_ORIGINS`), with credentials, `GET`/`POST`/`OPTIONS`, three allowed headers and a 3,600-second max age. `ApiGatewayApplicationTests` verifies a `200` preflight and the allow-origin/credentials headers. |
| Completed | Frontend catalogue rendering | The separate `iamandroid_lms_frontend` renders seeded images, badges, categories, tags, duration, rating, students and generated markers from the enriched API response. Detail-page metadata and remote image patterns were added; `npm run build` compiles and generates all routes successfully. |

### Verification command and result

Windows PowerShell, from the repository root:

```powershell
$env:EUREKA_CLIENT_REGISTER_WITH_EUREKA='false'
$env:EUREKA_CLIENT_FETCH_REGISTRY='false'
$env:LOGGING_LEVEL_ROOT='WARN'
$env:MONGODB_URI='mongodb://127.0.0.1:27017/course_db'
.\mvnw.cmd -q -pl auth-service,course-service,order-service -am clean test
```

Result: **3 tests run, 3 passed, 0 failures, 0 errors** (one `contextLoads` per service). The datasource defaults pointed at the recreated local databases. Hibernate created the JPA tables and `init.sql` seeded `auth_db` (2 users, 3 roles, 3 user-role links), `course_db` (1 category, 1 course, 1 module, 1 lesson) and `order_db` (1 enrollment). Re-running the seeds remains safe because they use fixed UUIDs and conflict handling. Mockito emitted a self-attachment warning; it did not fail the build. MongoDB server availability and Java 17 runtime execution remain unverified by these context tests.

### Incremental verification (2026-11-26)

With Eureka registration and fetching disabled, root logging set to `WARN` and `MONGODB_URI` pointed at loopback:

| Module command | Result |
| --- | --- |
| `.\mvnw.cmd -q -pl auth-service -am test` | **3 tests**, 3 passed, 0 failures/errors (before the login test was added); **4 tests**, 4 passed, 0 failures/errors after adding the no-transaction Basic-auth regression test. |
| `.\mvnw.cmd -q -pl api-gateway -am test` | **5 tests**, 5 passed, 0 failures/errors (before the public-login test was added); **11 tests**, 11 passed, 0 failures/errors after the filter hardening; **13 tests**, 13 passed, 0 failures/errors after adding public course GET coverage. |
| `.\mvnw.cmd -q -pl course-service -am test` | **6 tests**, 6 passed, 0 failures/errors. |
| `.\mvnw.cmd -q -pl course-service,order-service -am test` | **13 tests**, 13 passed, 0 failures/errors. |
| `.\mvnw.cmd -fae verify` | All **8 reactor modules** succeeded; **34 tests** passed, 0 failures/errors/skips. |

The course test created a category, course, module and lesson through the HTTP API and rolled the writes back. The order test used a mocked course-service response for lesson counts and rolled enrolment/progress writes back. The auth test registered an account, logged in with correct and incorrect credentials, rolled its writes back, and separately verified Basic authentication with the test transaction suspended so lazy roles cannot be loaded by accident. The gateway test checked that public login strips client identity headers and Basic credentials, that public course GETs need no credentials while course POSTs still do, that protected auth routes keep Basic credentials, that downstream errors are not converted to `401`, and that malformed, disabled or empty identity responses are rejected. Mockito's self-attachment warning and Hibernate's container `@Valid` deprecation warning persisted and did not fail any run. Java 17 runtime execution and MongoDB operations remain unverified by these module tests.

A follow-up live smoke test started fresh Eureka, auth, course, order and gateway processes and ran all 22 Postman collection requests through the gateway on `:8090`, plus one negative check that an unauthenticated course write returns `401`. Every request matched its expected status code, including both login requests, `/me`, four public course reads, four course writes, four order progress requests and three Eureka endpoints. The created lesson reached `100%` progress. The test used disposable `probe.gw+*` and `GW *` records; those records were not removed. AI and notification services still have no application endpoints to exercise.

### Catalogue and CORS verification (2026-11-26)

Windows PowerShell, from the repository root, with Eureka registration/fetching disabled and `MONGODB_URI` pointed at loopback:

```powershell
$env:MONGODB_URI='mongodb://127.0.0.1:27017/course_db'
$env:EUREKA_CLIENT_REGISTER_WITH_EUREKA='false'
$env:EUREKA_CLIENT_FETCH_REGISTRY='false'
.\mvnw.cmd -pl course-service,api-gateway -am test
```

Result: **BUILD SUCCESS**; 20 tests ran with 0 failures, 0 errors and 0 skips (14 gateway, 6 course-service). The course API test observed at least 50 catalogue records including `autocadtraining` with `type=COURSE`; the gateway preflight test received `200 OK` with `Access-Control-Allow-Origin: http://localhost:3000` and `Access-Control-Allow-Credentials: true`. The unused MongoDB client logged a loopback connection refusal without affecting the JPA-backed catalogue. From the separate frontend repository, `npm run build` compiled successfully and generated all routes. Direct browser CORS was verified at the gateway layer; the frontend BFF proxy path remains the normal browser flow.
