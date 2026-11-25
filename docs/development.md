# Local development

Run Maven commands from the repository root. The root POM coordinates seven independently launched Spring Boot applications. See the [architecture and routing reference](architecture.md) for request paths and the [audit](audit.md) for the build and test results from initialization.

## Toolchain and dependency inventory

| Component | Repository evidence | Developer impact |
| --- | --- | --- |
| Java | `java.version` is `17` in the root and module POMs | Use a JDK and check which Java installation Maven actually uses. |
| Spring Boot | Parent `4.2.0`, Boot Maven plugin, application version `0.0.1-SNAPSHOT` | The Boot dependency management supplies versions for most starters, drivers and test libraries. |
| Spring Cloud | Imported `spring-cloud-dependencies` BOM `2026.0.0` | Controls the Cloud dependencies used by discovery and gateway. |
| Maven | Wrapper `3.3.4`, `only-script`, requests Apache Maven `3.10.1` | Root and module wrappers specify the same distribution. The first run needs the distribution and access to configured artifact repositories, or populated local caches. |
| Persistence and tests | PostgreSQL JDBC, Hibernate/JPA, Spring Data MongoDB and JUnit Jupiter via managed dependencies | The POMs specify client libraries. The repository does not pin PostgreSQL or MongoDB **server** versions. |

Six modules inherit the root POM, including its Eureka client dependency. `eureka-server` participates in the reactor and inherits Spring Boot directly, importing the Cloud BOM itself. `api-gateway` also redeclares the Cloud BOM. The gateway uses reactive Spring Cloud Gateway and WebFlux. AI and notification add Web MVC. Auth adds Web MVC, JPA, PostgreSQL JDBC and Spring Security. Order adds Web MVC, JPA, PostgreSQL JDBC and validation. Course adds the order module's technology set plus MongoDB and Spring Data MongoDB starters. On JDK 23 and newer, `javac` does not discover Lombok from the classpath; the root POM declares an explicit `annotationProcessorPaths` entry and `proc=full`. [Root POM](../pom.xml) · [Gateway POM](../api-gateway/pom.xml) · [Eureka POM](../eureka-server/pom.xml) · [Wrapper settings](../.mvn/wrapper/maven-wrapper.properties)

The service JARs inspected in the audit contained Spring Framework 7.1.0, Cloud Gateway WebFlux / Netflix Eureka client / LoadBalancer 5.1.0, Spring Security 7.2.0 in auth, Hibernate 7.4.15.Final and PostgreSQL JDBC 42.7.14 in the three JPA modules, MongoDB sync driver 5.13.0 in course, and Logback 1.6.6. Eureka included Actuator 4.2.0 and Micrometer Core 1.18.0. Recheck effective versions after POM changes with `.\mvnw.cmd -pl <module> dependency:tree`.

## Prepare local data services

Auth, course and order expect PostgreSQL on `localhost:5432` with `auth_db`, `course_db` and `order_db` respectively. The AI properties also name `ai_db`, but the AI POM has no JDBC/JPA starter or PostgreSQL driver. Course additionally configures a MongoDB client. The repository supplies no Compose environment that starts these databases.

For a fresh, disposable **local** PostgreSQL instance, connect to the `postgres` maintenance database as a local administrator using `psql -h localhost -p 5432 -U postgres -d postgres`. The following creates a development login and the three databases used by the JPA services. `\password` prompts for the password without putting it in the command. If you already have dedicated development databases, use their connection details instead. Keep the reference bootstrap under `docs/` separate from this setup.

```sql
CREATE ROLE lms_local LOGIN;
\password lms_local
CREATE DATABASE auth_db OWNER lms_local;
CREATE DATABASE course_db OWNER lms_local;
CREATE DATABASE order_db OWNER lms_local;
```

For course, start a local MongoDB instance on port `27017`. For example, start `mongod --bind_ip 127.0.0.1 --port 27017 --dbpath <existing-local-data-directory>` with an empty directory reserved for development. The course properties now fall back to a local URI. Override `MONGODB_URI` when your MongoDB server uses authentication, a different address or a different database name.

## Configure each terminal

| Variable | Applies to | Effect |
| --- | --- | --- |
| `SERVER_PORT` | All seven modules | Explicit placeholder overriding that module's HTTP port. Set individually when changing ports. |
| `EUREKA_HOSTNAME` | Eureka server | Explicit placeholder. Defaults to `localhost` and contributes to the server's advertised registry URL. |
| `EUREKA_SERVER_URL` | Six clients, including gateway | Explicit placeholder. Defaults to `http://localhost:8761/eureka/`. Update clients when the registry address changes. |
| `MONGODB_URI` | Course | Explicit placeholder for `spring.mongodb.uri`. Use a local URI matching your MongoDB authentication settings. |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Auth, course, order, AI | Placeholders used by the datasource properties. The checked-in values are development fallbacks only; override them in the terminal for any shared environment. Standard Spring datasource overrides continue to work. |
| `SPRING_DATASOURCE_URL` | Auth, course, order | Standard Spring override for the JDBC URL. Scope to **one service** when overriding. Leaving it unset preserves distinct database names during a reactor test. |

Example for the disposable databases above, in PowerShell. Set these values in the terminal that will start a service or run tests. On systems with authenticated MongoDB, supply its URI through your local secret configuration.

```powershell
$env:POSTGRES_USER = 'lms_local'
$env:POSTGRES_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Local PostgreSQL password' -AsSecureString)).Password
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017/course_db'
```

Use the following commands in Bash.

```bash
export POSTGRES_USER=lms_local
read -rs -p 'Local PostgreSQL password: ' POSTGRES_PASSWORD
echo
export POSTGRES_PASSWORD
export MONGODB_URI='mongodb://127.0.0.1:27017/course_db'
```

## Build and test

| Task | Windows PowerShell | Unix shell |
| --- | --- | --- |
| Check Maven and its JDK | `.\mvnw.cmd -version` | `sh ./mvnw -version` |
| Run the reactor tests | `.\mvnw.cmd test` | `sh ./mvnw test` |
| Test one module and required reactor projects | `.\mvnw.cmd -pl course-service -am test` | `sh ./mvnw -pl course-service -am test` |
| Package and test the applications | `.\mvnw.cmd package` | `sh ./mvnw package` |

Every module has one JUnit Jupiter `@SpringBootTest` with an empty `contextLoads()` body. A pass confirms that the application context started with the configuration used for that run. There are no test-specific datastore fixtures or profiles in module sources. Set the local datasource and MongoDB overrides before running the full suite. Surefire writes per-module results to `<module>/target/surefire-reports/`. A package produced with `-DskipTests` does not validate startup.

## Start and stop

Start Eureka first. Run each service in its own terminal, with the appropriate environment from above. Use `-pl` to select **one** application for the `spring-boot:run` goal. The root project itself has `pom` packaging.

| Step | Module | Default port |
| --- | --- | ---: |
| Start the registry | `eureka-server` | 8761 |
| Start the backends you need | `ai-service`, `auth-service`, `course-service`, `notification-service`, `order-service` | 8081, 8082, 8083, 8084, 8085 respectively |
| Start the gateway | `api-gateway` | 8080 |

```powershell
.\mvnw.cmd -pl eureka-server spring-boot:run
```

```sh
sh ./mvnw -pl eureka-server spring-boot:run
```

Repeat the command in a new terminal with the desired module name. After packaging, the equivalent standalone registry command is `java -jar eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar`. Open `http://localhost:8761/` to inspect registered instances. Allow time for client registration and gateway discovery refresh. Stop each foreground process with `Ctrl+C`.

## Operations and troubleshooting

| Symptom | What to check |
| --- | --- |
| Maven cannot resolve the wrapper, parent or BOM | Inspect the first failing artifact URL, local Maven cache, mirror/proxy settings and the JDK shown by `-version`. A download failure precedes compilation and tests. |
| A JPA context fails to start | Check that the correct database exists and the development login can connect. The current JPA configuration requests `ddl-auto=update`, so use isolated development databases. |
| Course logs MongoDB connection or DNS errors | Verify `MONGODB_URI` is set in this process, the host/port is reachable, and MongoDB credentials match the URI. |
| Service registration fails or a gateway route has no healthy instance | Check `EUREKA_SERVER_URL`, server port and actual service name in Eureka. The [architecture reference](architecture.md) distinguishes gateway forwarding rules from implemented endpoints. |
| A process cannot bind its port | Check for another instance. Stop the intended local process or change its `SERVER_PORT` in that terminal. |
| An expected metric or health endpoint is missing | The properties request `health,info,metrics,prometheus` and an application tag. Endpoint availability depends on the resolved Actuator and registry libraries. Inspect the module's dependency tree and startup output. The repository supplies no Prometheus scrape configuration or Grafana provisioning. |

Use the application console for startup exceptions, registry activity and shutdown messages. The JPA configurations enable SQL output, formatting and highlighting. Protect logs that contain application data or development credentials. No repository-defined file logging, centralized log shipping, or deployment runbook was found. Configuration sources include the [course properties](../course-service/src/main/resources/application.properties), [gateway properties](../api-gateway/src/main/resources/application.properties) and [registry properties](../eureka-server/src/main/resources/application.properties).
