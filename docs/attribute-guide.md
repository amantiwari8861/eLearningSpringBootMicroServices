# LMS database attribute guide

This guide covers the 34 entities and 157 columns in the LMS relational diagram. Each service database has an `lms` schema. A UUID primary key identifies one row. A local foreign key is enforced by PostgreSQL. A cross-service reference is resolved and authorized through the owning service. Times are stored as `timestamptz`.

## Database ownership

| Database | Contents | Tables |
|---|---|---:|
| `auth_db` | Identity and authorization | 9 |
| `course_db` | Catalog and publishing | 7 |
| `order_db` | Orders and settlement | 7 |
| `learning_db` | Course access and learning records | 7 |
| `notification_db` | Notifications | 2 |
| `ai_db` | Derived course search | 1 |
| `audit_db` | Security audit | 1 |

## Reading a purchase from identity to progress

1. The identity provider validates a sign-in. The issuer and subject resolve to an `external_identity`, which points to an `app_user`.
2. `membership`, `role_grant` and `role_permission` establish organization-level privileges. An instructor also needs the correct `course_staff` assignment and an active account, organization and membership.
3. Checkout validates the published release and current offer. `order_line` freezes its course, release, price, tax and access duration. The order service verifies the merchant, amount, currency and signature of the provider event.
4. A verified capture updates the order and writes its `outbox_event` in one transaction. A durable relay publishes it. Learning records the message in its own `event_inbox` and issues an `access_grant` in one transaction.
5. A purchase grant uses `source_kind = 'purchase'` and the original order line UUID as `source_ref`. Playback requires a grant for the learner and course with `starts_at <= now()`, an absent or future `ends_at` and an absent or future `revoked_at`. Refund policy decides which purchase grant to revoke. Another independently issued grant can still provide access.
6. `enrollment` pins a course release. Multiple releases can each have their own enrollment and progress. A refund preserves the learning history.

## Installation and runtime

Run `lms-platform.sql` once with PostgreSQL 16+ and the **psql client**. The script starts in the `postgres` maintenance database and switches between databases. Use a cluster provisioning superuser on a fresh target, or resume an interrupted installation made by this same baseline. If existing database names or reserved roles belong to another installation, it stops before adopting them. A completed database is recognized by its owner and version marker. Later changes belong in migrations. Use `psql -X` and leave autocommit enabled during database creation.

```powershell
psql -X -h localhost -p 5432 -U postgres -d postgres -f ".\lms-platform.sql"
```

Each database has a NOLOGIN owner (`lms_<service>_owner`) and a NOLOGIN runtime group (`lms_<service>_runtime`). A separately provisioned service login receives the matching runtime group. For example, an administrator can run `GRANT lms_course_runtime TO your_course_service_login;` once that login exists. The audit writer can insert events and the separate `lms_audit_reader` group can read them. Database superusers and owners remain privileged. Export audit records to separately controlled storage for tamper-resistant retention.

### Transaction and integration rules

- Create an order in the pending state, insert its lines, and commit them together. Record a full capture and the transition to paid in the same transaction. Settled orders cannot be reopened or receive extra lines. Commit a refund and its allocations together. Money writes run at **SERIALIZABLE**, the configured default in `order_db`. Retry the entire transaction on SQLSTATE `40001`. Defer balance checks until commit so line items can be inserted in any FK-valid order. Pending and succeeded refunds both reserve funds. Update the order's refund state in the same transaction as marking the refund succeeded.
- A paid order must have captured its full total. A zero-total order can be completed through a separate free-checkout path. The schema accepts free offers and prevents spending a captured amount twice. Payment-provider verification, event publication, refund eligibility, and reconciliation are service responsibilities.
- The learning and notification inboxes are independent. Persist the consumer effect and its receipt atomically. Consumers must also track or reconcile the latest order sequence so a delayed capture cannot restore refunded access. A relay may use CDC or a durable external checkpoint for the order outbox.
- Before issuing a grant or recording progress, validate external references, the course-to-release relationship, and the lesson's membership in that release. Filter AI retrieval and cached responses by the caller's current course access. Sign private media URLs only after checking entitlement and scan status.
- The release-publishing trigger freezes the release and its curriculum. A media scanner can change its verdict after publication. Private object keys should identify immutable object versions. Financial line snapshots are append-only for the runtime role.
- `ai_db` and `audit_db` are concrete PostgreSQL names for the diagram's derived search and audit stores. `search_chunk.searchable_content` uses built-in PostgreSQL full-text search. `source_kind` makes the grant source explicit. Opaque provider IDs are text. Application-owned references are UUIDs.

## Identity and authorization — `auth_db`

Application identities, organization-scoped membership and permission assignments. The identity provider manages credentials and MFA.

### `app_user`

Local account to which sign-ins and learning records resolve.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this app user record. |
| `status` | `text` | Yes | Account lifecycle. Suspended and closed accounts must be denied access. |
| `display_name` | `text` | Yes | Human-readable name for profiles and staff screens. It is not a sign-in key. |

### `organization`

School, employer or publishing organization.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this organization record. |
| `name` | `text` | Yes | Display name used in the organization directory. |
| `status` | `text` | Yes | Whether the organization may operate or authorize members. |

### `external_identity`

Maps an identity asserted by a trusted OIDC issuer to one local account.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this external identity record. |
| `user_id` | `uuid` | Yes | Local account to sign in after the external assertion is validated. |
| `issuer` | `varchar(512)` | Yes | Exact, case-sensitive issuer identifier from the validated token. |
| `subject` | `varchar(255)` | Yes | Opaque provider subject. The issuer and subject together identify one sign-in identity. |

Table constraints: `UNIQUE (issuer, subject)`.

### `sso_connection`

Binds an organization to a configured enterprise identity connection.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this sso connection record. |
| `organization_id` | `uuid` | Yes | Organization that trusts this enterprise sign-in connection. |
| `broker_connection_ref` | `text` | Yes | Opaque connection identifier at the identity broker. Store connection secrets in the broker or secret manager. |

Table constraints: `UNIQUE (organization_id, broker_connection_ref)`.

### `membership`

Membership of one account in one organization.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this membership record. |
| `organization_id` | `uuid` | Yes | Organization granting the membership. |
| `user_id` | `uuid` | Yes | Local account holding the membership. |
| `status` | `text` | Yes | Only active membership can authorize organization-scoped actions. |

Table constraints: `UNIQUE (organization_id, user_id)`.

### `role`

Named bundle of permissions, such as instructor.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this role record. |
| `code` | `text` | Yes | Stable role identifier used by authorization policies. |

Table constraints: `UNIQUE (code)`.

### `permission`

Operation that can be granted through a role.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this permission record. |
| `code` | `text` | Yes | Stable action identifier, for example course.edit or refund.approve. |

Table constraints: `UNIQUE (code)`.

### `role_permission`

Assigns one permission to one role.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this role permission record. |
| `role_id` | `uuid` | Yes | Role receiving the permission. |
| `permission_id` | `uuid` | Yes | Action included in the role. |

Table constraints: `UNIQUE (role_id, permission_id)`.

### `role_grant`

Assigns a role to an organization membership.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this role grant record. |
| `membership_id` | `uuid` | Yes | Membership that establishes the scope of the grant. |
| `role_id` | `uuid` | Yes | Role granted within that organization. |
| `expires_at` | `timestamptz` | No | End of the assignment. NULL means no scheduled expiry. |

Table constraints: `UNIQUE (membership_id, role_id)`.

## Catalog and publishing — `course_db`

Versioned courses and their offers. Published releases are immutable. Access to media also requires a clean scan and a current learning grant.

### `course`

Catalog entry that persists across releases.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this course record. |
| `organization_ref` | `uuid` | Yes | Owning auth_db.organization.id. The catalog verifies the publisher scope. |
| `slug` | `text` | Yes | URL-friendly name, unique within the publishing organization. |
| `status` | `text` | Yes | Catalog visibility and sale lifecycle. Archive removes the course from new sales. |

Table constraints: `UNIQUE (organization_ref, slug)`.

### `course_staff`

Assigns a user a duty for a particular course.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this course staff record. |
| `course_id` | `uuid` | Yes | Course covered by the assignment. |
| `user_ref` | `uuid` | Yes | Assigned auth_db.app_user.id. Check current membership and permissions before allowing an action. |
| `duty` | `text` | Yes | Course-specific responsibility of the assigned person. |

Table constraints: `UNIQUE (course_id, user_ref, duty)`.

### `course_release`

Version of a course containing the curriculum learners enroll against.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this course release record. |
| `course_id` | `uuid` | Yes | Course whose curriculum this release represents. |
| `version_no` | `integer` | Yes | Positive revision number scoped to the course. |
| `published_at` | `timestamptz` | No | Time this revision became immutable. NULL identifies an editable draft. |

Table constraints: `UNIQUE (course_id, version_no)`.

### `section`

Ordered section within a release.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this section record. |
| `release_id` | `uuid` | Yes | Course release containing this section. |
| `position` | `integer` | Yes | One-based ordering within the release. |
| `title` | `text` | Yes | Section title shown in the curriculum. |

Table constraints: `UNIQUE (release_id, position) DEFERRABLE INITIALLY IMMEDIATE`.

### `lesson`

Ordered learning item within a section.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this lesson record. |
| `section_id` | `uuid` | Yes | Section containing this lesson. |
| `position` | `integer` | Yes | One-based lesson ordering within the section. |
| `title` | `text` | Yes | Lesson title displayed to the learner. |

Table constraints: `UNIQUE (section_id, position) DEFERRABLE INITIALLY IMMEDIATE`.

### `media_asset`

Reference to a private object used by a lesson.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this media asset record. |
| `lesson_id` | `uuid` | Yes | Lesson that uses this asset. |
| `object_key` | `text` | Yes | Private object-storage key. Issue a short-lived delivery URL only after an access check. |
| `scan_state` | `text` | Yes | Current scanner verdict. Only clean assets are eligible for delivery. |

Table constraints: `UNIQUE (lesson_id, object_key)`.

### `offer`

Price and access terms for a course during a time window.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this offer record. |
| `course_id` | `uuid` | Yes | Course sold by this offer. |
| `currency` | `text` | Yes | Three-letter currency code. The payment integration validates the supported currency and its minor-unit exponent. |
| `price_minor` | `bigint` | Yes | Price in integer currency minor units. Zero supports a free offer. |
| `valid_from` | `timestamptz` | Yes | First instant when checkout may accept this offer. |
| `valid_until` | `timestamptz` | No | Exclusive end of the offer window. NULL means ongoing. |
| `access_days` | `integer` | No | Days of access from activation. NULL means access has no scheduled end. |

Table constraints: `CHECK (valid_until IS NULL OR valid_until > valid_from)`.

## Orders and settlement — `order_db`

Purchases, payment attempts, refund allocations and integration events. One line buys one course seat. Currency is inherited from the order by its lines and refunds.

### `purchase_order`

One checkout and its financial total.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this purchase order record. |
| `buyer_ref` | `uuid` | Yes | Purchasing auth_db.app_user.id. |
| `currency` | `text` | Yes | Three-letter currency code. The payment integration validates the supported currency and its minor-unit exponent. |
| `total_minor` | `bigint` | Yes | Sum of all line net amounts plus line taxes, in the order currency. |
| `state` | `text` | Yes | Checkout lifecycle. The service advances the state after provider verification and reconciliation. |

### `order_line`

Immutable snapshot of an accepted course purchase.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this order line record. |
| `order_id` | `uuid` | Yes | Checkout containing this purchase. |
| `offer_ref` | `uuid` | Yes | Accepted course_db.offer.id. |
| `course_ref` | `uuid` | Yes | Purchased course_db.course.id. Used when issuing a grant. |
| `release_ref` | `uuid` | Yes | Purchased course_db.course_release.id. Snapshot validated against the course at checkout. |
| `title_snapshot` | `text` | Yes | Course title displayed at the time of sale, retained for receipts. |
| `net_minor` | `bigint` | Yes | Line price after discounts and before tax. |
| `tax_minor` | `bigint` | Yes | Tax charged on this line, in order-currency minor units. |
| `access_days` | `integer` | No | Days of access from activation. NULL means access has no scheduled end. |

### `payment_attempt`

Provider attempt to collect funds for an order.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this payment attempt record. |
| `order_id` | `uuid` | Yes | Order being funded. |
| `provider` | `text` | Yes | Payment provider code; scopes the provider identifiers. |
| `merchant_ref` | `text` | Yes | Opaque merchant-account identifier at that provider. |
| `payment_ref` | `text` | No | Provider payment identifier. NULL before the provider assigns one. |
| `amount_minor` | `bigint` | Yes | Amount requested or captured by this attempt. |
| `currency` | `text` | Yes | Three-letter currency code. The payment integration validates the supported currency and its minor-unit exponent. |
| `state` | `text` | Yes | Provider-confirmed lifecycle. A captured attempt keeps its captured state after refunds. |

Table constraints: `UNIQUE (provider, merchant_ref, payment_ref)`, `CHECK (state <> 'captured' OR payment_ref IS NOT NULL)`.

### `refund`

Provider refund against one captured payment.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this refund record. |
| `payment_attempt_id` | `uuid` | Yes | Captured payment against which funds are returned. |
| `refund_ref` | `text` | No | Provider refund identifier. NULL is allowed while a request is pending. |
| `amount_minor` | `bigint` | Yes | Positive amount reserved or returned against the captured payment. |
| `state` | `text` | Yes | Pending and successful refunds consume refundable balance. |

Table constraints: `UNIQUE (payment_attempt_id, refund_ref)`, `CHECK (state <> 'succeeded' OR refund_ref IS NOT NULL)`.

### `refund_line`

Allocates part of a refund to a purchased line.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this refund line record. |
| `refund_id` | `uuid` | Yes | Refund containing this allocation. |
| `order_line_id` | `uuid` | Yes | Original line whose refundable balance is consumed. |
| `amount_minor` | `bigint` | Yes | Positive share of the refund applied to this line. |

Table constraints: `UNIQUE (refund_id, order_line_id)`.

### `webhook_receipt`

Deduplicates provider notifications by merchant and event identifier.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this webhook receipt record. |
| `provider` | `text` | Yes | Provider that sent the signed callback. |
| `merchant_ref` | `text` | Yes | Provider merchant account receiving this event. |
| `event_ref` | `text` | Yes | Opaque provider event identifier, used to detect redelivery. |
| `state` | `text` | Yes | Outcome of durable webhook processing; failed records are eligible for retry. |

Table constraints: `UNIQUE (provider, merchant_ref, event_ref)`.

### `outbox_event`

Event written in the same transaction as the order change, for publication by a durable relay.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this outbox event record. |
| `event_key` | `uuid` | Yes | Stable message ID shared with subscribers for deduplication. |
| `order_id` | `uuid` | Yes | Order whose change produced the event. |
| `seq` | `bigint` | Yes | Increasing order-local sequence assigned by the publisher to detect stale or missing events. |
| `type` | `text` | Yes | Versioned event type describing the order transition. |
| `payload` | `jsonb` | Yes | Event data for consumers. Include necessary references and amounts and exclude credentials and card data. |

Table constraints: `UNIQUE (event_key)`, `UNIQUE (order_id, seq)`.

## Course access and learning records — `learning_db`

Entitlements authorize playback. Enrollments preserve progress independently of access. Each enrollment pins one release.

### `access_grant`

An independently revocable source of access for one learner and course.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this access grant record. |
| `learner_ref` | `uuid` | Yes | Beneficiary auth_db.app_user.id. |
| `course_ref` | `uuid` | Yes | Course covered by this grant, from course_db.course.id. |
| `source_kind` | `text` | Yes | Identifies which authority issued the grant and how to resolve source_ref. |
| `source_ref` | `uuid` | Yes | For purchase grants, order_db.order_line.id. For other grants, an issuance or assignment UUID from the granting workflow. |
| `starts_at` | `timestamptz` | Yes | Inclusive first instant when the learner may use this grant. |
| `ends_at` | `timestamptz` | No | Exclusive expiry instant. NULL means no scheduled expiry. |
| `revoked_at` | `timestamptz` | No | Revocation instant. NULL means the grant has not been revoked. |

Table constraints: `UNIQUE (source_kind, source_ref, learner_ref, course_ref)`, `CHECK (ends_at IS NULL OR ends_at > starts_at)`.

### `enrollment`

Learning history of a learner on a specific release of a course.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this enrollment record. |
| `learner_ref` | `uuid` | Yes | Learner auth_db.app_user.id. |
| `course_ref` | `uuid` | Yes | Stable course_db.course.id for this enrollment. |
| `release_ref` | `uuid` | Yes | Pinned course_db.course_release.id used to interpret lessons and assessments. |
| `joined_at` | `timestamptz` | Yes | Time the learner started this enrollment. |

Table constraints: `UNIQUE (learner_ref, course_ref, release_ref)`.

### `lesson_progress`

Completion record for one lesson within an enrollment.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this lesson progress record. |
| `enrollment_id` | `uuid` | Yes | Learner and release whose progress is recorded. |
| `lesson_ref` | `uuid` | Yes | course_db.lesson.id. The learning service verifies that it belongs to the enrolled release. |
| `completed_at` | `timestamptz` | No | Completion time. NULL represents an existing, incomplete progress record. |

Table constraints: `UNIQUE (enrollment_id, lesson_ref)`.

### `assessment`

Assessment definition scoped to a course release.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this assessment record. |
| `release_ref` | `uuid` | Yes | course_db.course_release.id containing this assessment. |
| `revision` | `integer` | Yes | Positive revision of the assessment definition. Use a new record for a changed assessment. |
| `pass_score` | `numeric(5,2)` | Yes | Minimum passing percentage on the 0 to 100 scale. |

### `attempt`

One assessment sitting by an enrolled learner.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this attempt record. |
| `assessment_id` | `uuid` | Yes | Assessment and scoring rules used for this sitting. |
| `enrollment_id` | `uuid` | Yes | Enrollment of the learner making the attempt. |
| `score` | `numeric(5,2)` | No | Awarded percentage. NULL until graded. |
| `submitted_at` | `timestamptz` | No | Submission time. NULL identifies a draft attempt. |

Table constraints: `CHECK (score IS NULL OR submitted_at IS NOT NULL)`.

### `certificate`

Verifiable completion credential issued for an enrollment.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this certificate record. |
| `enrollment_id` | `uuid` | Yes | Enrollment whose completion earned this credential. |
| `serial` | `text` | Yes | Unique public verification code, issued by the learning service. |
| `issued_at` | `timestamptz` | Yes | Time this particular credential was issued. |
| `revoked_at` | `timestamptz` | No | Time this credential was withdrawn. NULL means it has not been revoked. |

Table constraints: `UNIQUE (serial)`, `CHECK (revoked_at IS NULL OR revoked_at >= issued_at)`.

### `event_inbox`

Processed message receipt. Insert in the same transaction as the consumer action.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this event inbox record. |
| `consumer` | `text` | Yes | Stable subscriber name. Each independent subscriber can process the same event once. |
| `event_key` | `uuid` | Yes | Message UUID supplied by the publisher, such as order_db.outbox_event.event_key. |
| `processed_at` | `timestamptz` | Yes | Time the consumer recorded successful processing. |

Table constraints: `UNIQUE (consumer, event_key)`.

## Notifications — `notification_db`

Durable delivery queue and independent consumer receipts.

### `event_inbox`

Processed message receipt. Insert in the same transaction as the consumer action.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this event inbox record. |
| `consumer` | `text` | Yes | Stable subscriber name. Each independent subscriber can process the same event once. |
| `event_key` | `uuid` | Yes | Message UUID supplied by the publisher, such as order_db.outbox_event.event_key. |
| `processed_at` | `timestamptz` | Yes | Time the consumer recorded successful processing. |

Table constraints: `UNIQUE (consumer, event_key)`.

### `delivery`

Delivery of an event to one recipient through one channel.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this delivery record. |
| `event_key` | `uuid` | Yes | Message UUID driving this notification. Use it with recipient and channel for idempotency. |
| `recipient_ref` | `uuid` | Yes | Destination auth_db.app_user.id. Resolve contact details through the authorized identity or profile source. |
| `channel` | `text` | Yes | Delivery route chosen for the recipient. |
| `state` | `text` | Yes | Delivery lifecycle. Workers select queued and retryable rows. |
| `next_try_at` | `timestamptz` | No | Earliest retry time. NULL means no delay is scheduled. |

Table constraints: `UNIQUE (event_key, recipient_ref, channel)`.

## Derived course search — `ai_db`

Rebuildable text search index. Authorization filters by release, lesson and current entitlement before content is returned.

### `search_chunk`

Searchable fragment derived from published course content.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this search chunk record. |
| `course_ref` | `uuid` | Yes | Owning course_db.course.id, used in course authorization and filtering. |
| `release_ref` | `uuid` | Yes | Exact course_db.course_release.id from which this text was indexed. |
| `lesson_ref` | `uuid` | Yes | Source course_db.lesson.id for retrieval and reindexing. |
| `searchable_content` | `text` | Yes | Text of this fragment. PostgreSQL builds a full-text index on it. |

## Security audit — `audit_db`

Append-only audit events. The writer can insert, and a separate reader role can query. Off-cluster retention is an operational responsibility.

### `audit_event`

Record of a security-sensitive action on a typed target.

| Attribute | PostgreSQL type | Required | Relevance |
|---|---|---|---|
| `id` | `uuid` | Yes | Stable identifier for this audit event record. |
| `actor_ref` | `uuid` | No | Human or service-principal UUID performing the action. NULL for an unattributed or system event. |
| `target_ref` | `uuid` | No | Identifier of the affected resource. NULL for an action with no individual target. |
| `action` | `text` | Yes | Namespaced verb identifying the affected resource type and operation, such as course.publish. |
| `occurred_at` | `timestamptz` | Yes | Time the audited action occurred, supplied by the producer or defaulted on receipt. |
| `correlation_key` | `uuid` | Yes | UUID linking audit events and requests across services. |

## Inspect the dictionary from PostgreSQL

Connect to any of the seven databases and run this query.

```sql
SELECT c.relname AS table_name, a.attname AS column_name,
       format_type(a.atttypid, a.atttypmod) AS data_type,
       a.attnotnull AS required, col_description(c.oid, a.attnum) AS relevance
FROM pg_catalog.pg_attribute AS a
JOIN pg_catalog.pg_class AS c ON c.oid = a.attrelid
JOIN pg_catalog.pg_namespace AS n ON n.oid = c.relnamespace
WHERE n.nspname = 'lms' AND c.relkind = 'r'
  AND a.attnum > 0 AND NOT a.attisdropped
ORDER BY c.relname, a.attnum;
```
