# eLearning Microservices Project

A Spring Boot and Spring Cloud backend for an LMS and course storefront. The current working tree provides a Eureka registry, a reactive API gateway and five service applications. The gateway has four routes. The service code is at the application-bootstrap stage. The repository also contains a separately executable PostgreSQL design for the intended platform.

## Start locally

Use a JDK that supports Java 17 compilation and the included Maven wrapper. From the repository root on Windows, these commands start Eureka and the gateway in **separate terminals**.

```powershell
# Terminal 1
.\mvnw.cmd -f eureka-server/pom.xml spring-boot:run
```

```powershell
# Terminal 2
.\mvnw.cmd -f api-gateway/pom.xml spring-boot:run
```

Open the [local registry dashboard](http://localhost:8761/) and allow time for registration. The gateway listens on port 8080. To start auth, course or order, first supply local database settings. Course-service includes a credential-bearing external MongoDB fallback, so explicitly set a local `MONGODB_URI` before starting it or running its tests. The [development guide](docs/development.md) covers configuration, per-module startup, Unix commands, troubleshooting and tests.

## Repository map

| Path | Purpose |
| --- | --- |
| `pom.xml`, `.mvn/wrapper/`, `mvnw`, `mvnw.cmd` | Project versions, module list and repeatable Maven invocation |
| `eureka-server/`, `api-gateway/` | Service registration and HTTP routing |
| `auth-service/`, `course-service/`, `order-service/` | MVC application entry points with persistence dependencies |
| `ai-service/`, `notification-service/` | MVC application entry points and discovery clients |
| `<module>/src/main/`, `<module>/src/test/` | Boot main class, application properties and one Spring context test per module |
| `docs/` | System documentation, audit, decision records, SQL bootstrap and design diagrams |

## Developer references

| For this task | Read |
| --- | --- |
| Working agreements, permissions and verification commands | [AGENTS.md](AGENTS.md) |
| Framework versions, dependency inventory, environment variables, running and testing | [Development guide](docs/development.md) |
| Module ports, architecture diagram, route matching and framework endpoints | [Architecture and HTTP surface](docs/architecture.md) |
| Measured HTTP responses, build results, security findings and prioritized roadmap | [Repository audit](docs/audit.md) |
| Current persistence and proposed schema, relationships and constraints | [Database inventory](docs/database.md) · [Relational diagram](docs/lms-course-platform-erd-77a21795.svg) · [Attribute guide](docs/attribute-guide.md) |
| Decisions and proposals | [Service data ownership](docs/adr/0001-service-data-ownership.md) · [Gateway routing](docs/adr/0002-explicit-gateway-routes.md) |
