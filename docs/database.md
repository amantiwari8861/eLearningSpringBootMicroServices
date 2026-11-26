# Database inventory

## What the applications configure

The JPA entities, not the standalone design SQL, define the current application-owned schema. Auth owns `users`, `roles` and `user_roles`; course owns `categories`, `courses`, `course_tags`, `course_modules` and `lessons`; order owns `enrollments` and `lesson_progress`. The `courses` table now also holds `type`, `subcategory`, `duration`, `rating`, `students`, `thumbnail_url`, `course_url`, `featured` and `is_generated`; tags live in `course_tags` with a unique `(course_id, tag)` key. Each module also has an idempotent `init.sql` seed that runs after Hibernate schema update. Course additionally loads its 50-item catalogue from `catalog/learning-data.json` on startup and skips slugs that already exist. Progress stores one row per enrolment and lesson, with a unique key on `(enrollment_id, lesson_id)`. No MongoDB document classes exist; the course MongoDB configuration is unused by application code.

| Module | Configured storage | Evidence and scope |
| --- | --- | --- |
| `auth-service` | PostgreSQL, `localhost:5432/auth_db` | JPA entities `GenericUser` and `Role`; repositories and BCrypt-backed user details. Tables: `users`, `roles`, `user_roles`. [POM](../auth-service/pom.xml) · [properties](../auth-service/src/main/resources/application.properties) |
| `course-service` | PostgreSQL, `localhost:5432/course_db`, plus configured MongoDB | JPA entities `Category`, `Course`, `CourseModule` and `Lesson` with `BaseEntity` owning `id`, timestamps and `version`. Tables: `categories`, `courses`, `course_tags`, `course_modules`, `lessons`. `CatalogDataLoader` seeds 50 catalogue records by slug; `MONGODB_URI` overrides the unused Mongo connection and should be supplied through the environment for hosted instances. [POM](../course-service/pom.xml) · [properties](../course-service/src/main/resources/application.properties) · [loader](../course-service/src/main/java/in/iamandroid/course/seed/CatalogDataLoader.java) |
| `order-service` | PostgreSQL, `localhost:5432/order_db` | JPA entities `Enrollment` and `LessonProgress`. Tables: `enrollments`, `lesson_progress`. Progress percentages use a course-service lesson-count read; no course data is copied into `order_db`. [POM](../order-service/pom.xml) · [properties](../order-service/src/main/resources/application.properties) |
| `ai-service` | Properties name `localhost:5432/ai_db` | Its POM and parent supply neither JPA nor the PostgreSQL driver. Its Java entry point contains no database integration. [POM](../ai-service/pom.xml) · [properties](../ai-service/src/main/resources/application.properties) |
| `api-gateway`, `eureka-server`, `notification-service` | No application datastore configured | See the [gateway](../api-gateway/src/main/resources/application.properties), [registry](../eureka-server/src/main/resources/application.properties), and [notification](../notification-service/src/main/resources/application.properties) configurations. |

The three JPA modules keep `spring.jpa.hibernate.ddl-auto=update` and `spring.jpa.show-sql=true`. Starting their application contexts can update the schema and run seed scripts against the configured databases. Use disposable databases for tests and override connection settings with `SPRING_DATASOURCE_URL`, `POSTGRES_USER` and `POSTGRES_PASSWORD`. Supply a local `MONGODB_URI` when running course-service; its fallback is loopback. Keep actual credentials out of examples and logs.

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

There is no Flyway or Liquibase configuration or application migration directory. The service modules do use Spring SQL initialization: each has an idempotent `init.sql` that seeds fixed dummy rows after Hibernate updates the schema. Course also runs `CatalogDataLoader` after startup: it reads `catalog/learning-data.json`, creates missing categories and saves catalogue items whose slug is not already present. The 50 records comprise 38 courses and 12 blogs, all `PUBLISHED`, using the supplied working image URLs. This is application seed data, not a schema migration; `ddl-auto=update` remains an explicit local-development choice and schema ownership and migrations are still an open decision. The standalone `lms-platform.sql` requires a PostgreSQL 16+ cluster provisioning superuser and the `psql` client. It switches databases, creates roles, and installs a separate baseline per database. A rerun skips a completed schema by its marker and does not repair drift. Running it is a separate provisioning operation that changes the target cluster. Follow its instructions only when provisioning the design on an intended target. [Auth seed](../auth-service/src/main/resources/init.sql) · [Course seed](../course-service/src/main/resources/init.sql) · [Order seed](../order-service/src/main/resources/init.sql) · [Bootstrap header](lms-platform.sql)

## Proposed next decision

Choose the owner and migration process before mapping a business entity. The course module especially needs a decision about the role of MongoDB alongside the proposed PostgreSQL catalog. The alternatives and adoption criteria are recorded in [ADR 0001](adr/0001-service-data-ownership.md).
