# Database inventory

## What the applications configure

The Java modules currently contain application entry points and context-load tests. There are no JPA entity mappings, Spring Data repositories, MongoDB document classes, or application-owned table or collection definitions in their sources. Schema and record contents in a particular database depend on how its operator provisioned it.

| Module | Configured storage | Evidence and scope |
| --- | --- | --- |
| `auth-service` | PostgreSQL, `localhost:5432/auth_db` | Spring Data JPA and PostgreSQL JDBC driver. [POM](../auth-service/pom.xml) · [properties](../auth-service/src/main/resources/application.properties) |
| `course-service` | PostgreSQL, `localhost:5432/course_db`, plus MongoDB | JPA, PostgreSQL and MongoDB starters. `MONGODB_URI` overrides the Mongo connection. Automatic Mongo index creation is enabled. [POM](../course-service/pom.xml) · [properties](../course-service/src/main/resources/application.properties) |
| `order-service` | PostgreSQL, `localhost:5432/order_db` | Spring Data JPA and PostgreSQL JDBC driver. [POM](../order-service/pom.xml) · [properties](../order-service/src/main/resources/application.properties) |
| `ai-service` | Properties name `localhost:5432/ai_db` | Its POM and parent supply neither JPA nor the PostgreSQL driver. Its Java entry point contains no database integration. [POM](../ai-service/pom.xml) · [properties](../ai-service/src/main/resources/application.properties) |
| `api-gateway`, `eureka-server`, `notification-service` | No application datastore configured | See the [gateway](../api-gateway/src/main/resources/application.properties), [registry](../eureka-server/src/main/resources/application.properties), and [notification](../notification-service/src/main/resources/application.properties) configurations. |

The three JPA modules set `spring.jpa.hibernate.ddl-auto=update` and `spring.jpa.show-sql=true`. Starting their application contexts can open a connection. Use a disposable database for tests and supply its connection settings through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`. Supply a local `MONGODB_URI` when running course-service because the default targets a remote host and includes credentials. Keep actual credentials out of examples and logs.

## Standalone PostgreSQL design

The workspace also contains an executable PostgreSQL bootstrap, two diagrams, and a column dictionary. They describe a proposed service data model that can be provisioned independently. The Maven builds and Spring application entry points have no integration that applies this bootstrap. [SQL bootstrap](lms-platform.sql) · [Logical design](lms.svg) · [Relational diagram](lms-course-platform-erd-77a21795.svg) · [Attribute guide](attribute-guide.md)

The SQL defines 34 tables, 157 documented columns, and 24 local foreign keys across seven databases. Each database uses an `lms` schema.

| Design database | Tables | Key relationships and constraints in the SQL |
| --- | ---: | --- |
| `auth_db` | 9 | The issuer and subject uniquely identify an external identity. Membership joins users to organizations. Role grants and role-permission links scope actions to a membership. |
| `course_db` | 7 | A course has versioned releases. Sections belong to a release and lessons to a section. Triggers protect published curriculum while allowing a media scan verdict to change. |
| `order_db` | 7 | Orders have lines and payment attempts. Each refund allocation links a refund to an order line. Deferred checks validate amounts and order ownership. Financial writes require serializable transactions. |
| `learning_db` | 7 | Grants identify a source of access. Enrollments pin a release. Progress and assessment attempts refer to an enrollment. Unique keys deduplicate grants and consumer receipts. |
| `notification_db` | 2 | Deliveries are unique by event, recipient, and channel. A separate inbox deduplicates each consumer's events. |
| `ai_db` | 1 | Search chunks carry course, release, and lesson references. A PostgreSQL full-text index covers their searchable content. |
| `audit_db` | 1 | Events have actor, target and correlation references. The runtime group receives insert permission. A separate reader group receives select permission. |

Within a database, foreign keys enforce relationships such as `refund_line.order_line_id` to `order_line.id`. Across databases, IDs such as `learning_db.lms.access_grant.source_ref` refer to records owned elsewhere. For a purchase grant, the application must verify the referenced order line and process repeated events safely. The design includes inbox and outbox tables. Implementing a publisher, consumers, or an access policy requires application work. [Attribute guide](attribute-guide.md)

## Migrations and provisioning

There is no Flyway or Liquibase configuration, application migration directory, or automatic Spring SQL initialization script in the service modules. The standalone script requires a PostgreSQL 16+ cluster provisioning superuser and the `psql` client. It switches databases, creates roles, and installs a baseline per database. A rerun skips a completed schema by its marker and does not repair drift. Running the script is a separate provisioning operation that changes the target cluster. Follow its instructions only when provisioning the design on an intended target. [Bootstrap header](lms-platform.sql) · [Installation instructions](attribute-guide.md#installation-and-runtime)

## Proposed next decision

Choose the owner and migration process before mapping a business entity. The course module especially needs a decision about the role of MongoDB alongside the proposed PostgreSQL catalog. The alternatives and adoption criteria are recorded in [ADR 0001](adr/0001-service-data-ownership.md).
