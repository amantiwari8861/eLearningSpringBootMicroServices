-- LMS and course storefront | PostgreSQL bootstrap and data dictionary, v1
-- Run with PostgreSQL 16+ psql as a cluster provisioning superuser.
--   psql -X -h HOST -p PORT -U ADMIN -d postgres -f lms-platform.sql
-- Use a maintenance window for initial provisioning. No passwords are embedded.
-- This is ONE psql input file: CREATE DATABASE runs outside transactions and
-- \connect switches databases. Do not use --single-transaction or a GUI query editor.
-- Each database installs atomically. A retry skips marked, completed schemas.
-- Existing, independently owned databases or reserved roles cause refusal.
-- This baseline does not reconcile schema drift. Use migrations for later changes.
-- Application tables are in schema lms. Each database has a NOLOGIN owner and
-- a NOLOGIN runtime group. Grant the matching runtime group to a separately
-- provisioned service login. The audit reader group is separate from its writer.
-- Currency values use minor units. Cross-service UUID references use no database FK.
-- Table and column comments form a queryable data dictionary in each database.
\set ON_ERROR_STOP on
\set AUTOCOMMIT on
\connect postgres
SET search_path TO pg_catalog;
SELECT pg_advisory_lock(185214, 86401);
BEGIN;
DO $preflight$
DECLARE
    role_name text;
    db_name text;
    entry record;
BEGIN
    IF current_setting('server_version_num')::integer < 160000 THEN
        RAISE EXCEPTION 'This bootstrap targets PostgreSQL 16 or later';
    END IF;
    IF NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Run the bootstrap using a cluster provisioning superuser';
    END IF;
    FOREACH role_name IN ARRAY ARRAY['lms_auth_owner', 'lms_auth_runtime', 'lms_course_owner', 'lms_course_runtime', 'lms_order_owner', 'lms_order_runtime', 'lms_learning_owner', 'lms_learning_runtime', 'lms_notification_owner', 'lms_notification_runtime', 'lms_ai_owner', 'lms_ai_runtime', 'lms_audit_owner', 'lms_audit_runtime', 'lms_audit_reader'] LOOP
        SELECT * INTO entry FROM pg_roles WHERE rolname = role_name;
        IF FOUND AND (shobj_description(entry.oid, 'pg_authid') IS DISTINCT FROM 'lms-storefront-bootstrap/v1'
            OR entry.rolcanlogin OR entry.rolsuper OR entry.rolcreatedb OR entry.rolcreaterole
            OR entry.rolreplication OR entry.rolbypassrls) THEN
            RAISE EXCEPTION 'Reserved role % exists with different ownership metadata or privileges', role_name;
        END IF;
    END LOOP;
    FOREACH db_name IN ARRAY ARRAY['auth_db', 'course_db', 'order_db', 'learning_db', 'notification_db', 'ai_db', 'audit_db'] LOOP
        IF EXISTS (SELECT 1 FROM pg_database d JOIN pg_roles r ON r.oid = d.datdba
                   WHERE d.datname = db_name AND (r.rolname <> 'lms_' || split_part(db_name,'_',1) || '_owner'
                   OR shobj_description(r.oid,'pg_authid') IS DISTINCT FROM 'lms-storefront-bootstrap/v1')) THEN
            RAISE EXCEPTION 'Database % already exists outside this bootstrap; use a fresh cluster or reviewed migration', db_name;
        END IF;
    END LOOP;
    FOREACH role_name IN ARRAY ARRAY['lms_auth_owner', 'lms_auth_runtime', 'lms_course_owner', 'lms_course_runtime', 'lms_order_owner', 'lms_order_runtime', 'lms_learning_owner', 'lms_learning_runtime', 'lms_notification_owner', 'lms_notification_runtime', 'lms_ai_owner', 'lms_ai_runtime', 'lms_audit_owner', 'lms_audit_runtime', 'lms_audit_reader'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
            EXECUTE format('CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', role_name);
            EXECUTE format('COMMENT ON ROLE %I IS %L', role_name, 'lms-storefront-bootstrap/v1');
        END IF;
    END LOOP;
END
$preflight$;
COMMIT;

-- Provision new databases with connections disabled until their ACLs are ready.
SELECT 'CREATE DATABASE auth_db WITH OWNER lms_auth_owner TEMPLATE template0 ENCODING ''UTF8'' ALLOW_CONNECTIONS false'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'auth_db')
\gexec
BEGIN;
REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
GRANT CONNECT ON DATABASE auth_db TO lms_auth_runtime;
ALTER DATABASE auth_db SET search_path TO pg_catalog, lms;
ALTER DATABASE auth_db WITH ALLOW_CONNECTIONS true;
COMMIT;

SELECT 'CREATE DATABASE course_db WITH OWNER lms_course_owner TEMPLATE template0 ENCODING ''UTF8'' ALLOW_CONNECTIONS false'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'course_db')
\gexec
BEGIN;
REVOKE ALL ON DATABASE course_db FROM PUBLIC;
GRANT CONNECT ON DATABASE course_db TO lms_course_runtime;
ALTER DATABASE course_db SET search_path TO pg_catalog, lms;
ALTER DATABASE course_db WITH ALLOW_CONNECTIONS true;
COMMIT;

SELECT 'CREATE DATABASE order_db WITH OWNER lms_order_owner TEMPLATE template0 ENCODING ''UTF8'' ALLOW_CONNECTIONS false'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'order_db')
\gexec
BEGIN;
REVOKE ALL ON DATABASE order_db FROM PUBLIC;
GRANT CONNECT ON DATABASE order_db TO lms_order_runtime;
ALTER DATABASE order_db SET search_path TO pg_catalog, lms;
ALTER DATABASE order_db SET default_transaction_isolation TO 'serializable';
ALTER DATABASE order_db WITH ALLOW_CONNECTIONS true;
COMMIT;

SELECT 'CREATE DATABASE learning_db WITH OWNER lms_learning_owner TEMPLATE template0 ENCODING ''UTF8'' ALLOW_CONNECTIONS false'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'learning_db')
\gexec
BEGIN;
REVOKE ALL ON DATABASE learning_db FROM PUBLIC;
GRANT CONNECT ON DATABASE learning_db TO lms_learning_runtime;
ALTER DATABASE learning_db SET search_path TO pg_catalog, lms;
ALTER DATABASE learning_db WITH ALLOW_CONNECTIONS true;
COMMIT;

SELECT 'CREATE DATABASE notification_db WITH OWNER lms_notification_owner TEMPLATE template0 ENCODING ''UTF8'' ALLOW_CONNECTIONS false'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'notification_db')
\gexec
BEGIN;
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;
GRANT CONNECT ON DATABASE notification_db TO lms_notification_runtime;
ALTER DATABASE notification_db SET search_path TO pg_catalog, lms;
ALTER DATABASE notification_db WITH ALLOW_CONNECTIONS true;
COMMIT;

SELECT 'CREATE DATABASE ai_db WITH OWNER lms_ai_owner TEMPLATE template0 ENCODING ''UTF8'' ALLOW_CONNECTIONS false'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ai_db')
\gexec
BEGIN;
REVOKE ALL ON DATABASE ai_db FROM PUBLIC;
GRANT CONNECT ON DATABASE ai_db TO lms_ai_runtime;
ALTER DATABASE ai_db SET search_path TO pg_catalog, lms;
ALTER DATABASE ai_db WITH ALLOW_CONNECTIONS true;
COMMIT;

SELECT 'CREATE DATABASE audit_db WITH OWNER lms_audit_owner TEMPLATE template0 ENCODING ''UTF8'' ALLOW_CONNECTIONS false'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'audit_db')
\gexec
BEGIN;
REVOKE ALL ON DATABASE audit_db FROM PUBLIC;
GRANT CONNECT ON DATABASE audit_db TO lms_audit_runtime, lms_audit_reader;
ALTER DATABASE audit_db SET search_path TO pg_catalog, lms;
ALTER DATABASE audit_db WITH ALLOW_CONNECTIONS true;
COMMIT;

-- Identity and authorization
\connect auth_db
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT pg_catalog.pg_advisory_xact_lock(185214, 86402);
SET LOCAL ROLE lms_auth_owner;
SET LOCAL search_path TO pg_catalog;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT (to_regnamespace('lms') IS NOT NULL) AS schema_exists
\gset
\if :schema_exists
DO $verify$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'lms'
        AND nspowner = current_user::regrole AND obj_description(oid,'pg_namespace') = 'lms-storefront-bootstrap/v1/auth') THEN
        RAISE EXCEPTION 'Unrecognized lms schema in auth_db; review before migration';
    END IF;
END
$verify$;
\echo auth_db: existing baseline v1 verified by marker
\else
CREATE SCHEMA lms AUTHORIZATION lms_auth_owner;
REVOKE ALL ON SCHEMA lms FROM PUBLIC;
GRANT USAGE ON SCHEMA lms TO lms_auth_runtime;
SET LOCAL search_path TO lms, pg_catalog;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'closed')),
    display_name text NOT NULL CHECK (btrim(display_name) <> '')
);
COMMENT ON TABLE app_user IS 'Local account to which sign-ins and learning records resolve.';
COMMENT ON COLUMN app_user.id IS 'Stable identifier for this app user record.';
COMMENT ON COLUMN app_user.status IS 'Account lifecycle. Suspended and closed accounts must be denied access.';
COMMENT ON COLUMN app_user.display_name IS 'Human-readable name for profiles and staff screens. It is not a sign-in key.';
GRANT SELECT, INSERT, UPDATE (status, display_name) ON TABLE app_user TO lms_auth_runtime;

CREATE TABLE organization (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL CHECK (btrim(name) <> ''),
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'closed'))
);
COMMENT ON TABLE organization IS 'School, employer or publishing organization.';
COMMENT ON COLUMN organization.id IS 'Stable identifier for this organization record.';
COMMENT ON COLUMN organization.name IS 'Display name used in the organization directory.';
COMMENT ON COLUMN organization.status IS 'Whether the organization may operate or authorize members.';
GRANT SELECT, INSERT, UPDATE ON TABLE organization TO lms_auth_runtime;

CREATE TABLE external_identity (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES app_user(id),
    issuer varchar(512) COLLATE "C" NOT NULL CHECK (issuer <> ''),
    subject varchar(255) COLLATE "C" NOT NULL CHECK (subject <> ''),
    UNIQUE (issuer, subject)
);
CREATE INDEX external_identity_idx_1 ON external_identity (user_id);
COMMENT ON TABLE external_identity IS 'Maps an identity asserted by a trusted OIDC issuer to one local account.';
COMMENT ON COLUMN external_identity.id IS 'Stable identifier for this external identity record.';
COMMENT ON COLUMN external_identity.user_id IS 'Local account to sign in after the external assertion is validated.';
COMMENT ON COLUMN external_identity.issuer IS 'Exact, case-sensitive issuer identifier from the validated token.';
COMMENT ON COLUMN external_identity.subject IS 'Opaque provider subject. The issuer and subject together identify one sign-in identity.';
GRANT SELECT, INSERT ON TABLE external_identity TO lms_auth_runtime;

CREATE TABLE sso_connection (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organization(id),
    broker_connection_ref text NOT NULL CHECK (btrim(broker_connection_ref) <> ''),
    UNIQUE (organization_id, broker_connection_ref)
);
COMMENT ON TABLE sso_connection IS 'Binds an organization to a configured enterprise identity connection.';
COMMENT ON COLUMN sso_connection.id IS 'Stable identifier for this sso connection record.';
COMMENT ON COLUMN sso_connection.organization_id IS 'Organization that trusts this enterprise sign-in connection.';
COMMENT ON COLUMN sso_connection.broker_connection_ref IS 'Opaque connection identifier at the identity broker. Store connection secrets in the broker or secret manager.';
GRANT SELECT, INSERT, UPDATE ON TABLE sso_connection TO lms_auth_runtime;

CREATE TABLE membership (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL REFERENCES organization(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    status text NOT NULL DEFAULT 'invited' CHECK (status IN ('invited', 'active', 'suspended', 'ended')),
    UNIQUE (organization_id, user_id)
);
CREATE INDEX membership_idx_1 ON membership (user_id);
COMMENT ON TABLE membership IS 'Membership of one account in one organization.';
COMMENT ON COLUMN membership.id IS 'Stable identifier for this membership record.';
COMMENT ON COLUMN membership.organization_id IS 'Organization granting the membership.';
COMMENT ON COLUMN membership.user_id IS 'Local account holding the membership.';
COMMENT ON COLUMN membership.status IS 'Only active membership can authorize organization-scoped actions.';
GRANT SELECT, INSERT, UPDATE (status) ON TABLE membership TO lms_auth_runtime;

CREATE TABLE role (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL CHECK (btrim(code) <> ''),
    UNIQUE (code)
);
COMMENT ON TABLE role IS 'Named bundle of permissions, such as instructor.';
COMMENT ON COLUMN role.id IS 'Stable identifier for this role record.';
COMMENT ON COLUMN role.code IS 'Stable role identifier used by authorization policies.';
GRANT SELECT, INSERT, UPDATE ON TABLE role TO lms_auth_runtime;

CREATE TABLE permission (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL CHECK (btrim(code) <> ''),
    UNIQUE (code)
);
COMMENT ON TABLE permission IS 'Operation that can be granted through a role.';
COMMENT ON COLUMN permission.id IS 'Stable identifier for this permission record.';
COMMENT ON COLUMN permission.code IS 'Stable action identifier, for example course.edit or refund.approve.';
GRANT SELECT, INSERT, UPDATE ON TABLE permission TO lms_auth_runtime;

CREATE TABLE role_permission (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id uuid NOT NULL REFERENCES role(id),
    permission_id uuid NOT NULL REFERENCES permission(id),
    UNIQUE (role_id, permission_id)
);
CREATE INDEX role_permission_idx_1 ON role_permission (permission_id);
COMMENT ON TABLE role_permission IS 'Assigns one permission to one role.';
COMMENT ON COLUMN role_permission.id IS 'Stable identifier for this role permission record.';
COMMENT ON COLUMN role_permission.role_id IS 'Role receiving the permission.';
COMMENT ON COLUMN role_permission.permission_id IS 'Action included in the role.';
GRANT SELECT, INSERT, DELETE ON TABLE role_permission TO lms_auth_runtime;

CREATE TABLE role_grant (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id uuid NOT NULL REFERENCES membership(id),
    role_id uuid NOT NULL REFERENCES role(id),
    expires_at timestamptz,
    UNIQUE (membership_id, role_id)
);
CREATE INDEX role_grant_idx_1 ON role_grant (role_id);
COMMENT ON TABLE role_grant IS 'Assigns a role to an organization membership.';
COMMENT ON COLUMN role_grant.id IS 'Stable identifier for this role grant record.';
COMMENT ON COLUMN role_grant.membership_id IS 'Membership that establishes the scope of the grant.';
COMMENT ON COLUMN role_grant.role_id IS 'Role granted within that organization.';
COMMENT ON COLUMN role_grant.expires_at IS 'End of the assignment. NULL means no scheduled expiry.';
GRANT SELECT, INSERT, UPDATE (expires_at), DELETE ON TABLE role_grant TO lms_auth_runtime;

COMMENT ON SCHEMA lms IS 'lms-storefront-bootstrap/v1/auth';
\endif
COMMIT;

-- Catalog and publishing
\connect course_db
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT pg_catalog.pg_advisory_xact_lock(185214, 86402);
SET LOCAL ROLE lms_course_owner;
SET LOCAL search_path TO pg_catalog;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT (to_regnamespace('lms') IS NOT NULL) AS schema_exists
\gset
\if :schema_exists
DO $verify$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'lms'
        AND nspowner = current_user::regrole AND obj_description(oid,'pg_namespace') = 'lms-storefront-bootstrap/v1/course') THEN
        RAISE EXCEPTION 'Unrecognized lms schema in course_db; review before migration';
    END IF;
END
$verify$;
\echo course_db: existing baseline v1 verified by marker
\else
CREATE SCHEMA lms AUTHORIZATION lms_course_owner;
REVOKE ALL ON SCHEMA lms FROM PUBLIC;
GRANT USAGE ON SCHEMA lms TO lms_course_runtime;
SET LOCAL search_path TO lms, pg_catalog;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE course (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_ref uuid NOT NULL,
    slug text NOT NULL CHECK (btrim(slug) <> ''),
    status text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'listed', 'archived')),
    UNIQUE (organization_ref, slug)
);
COMMENT ON TABLE course IS 'Catalog entry that persists across releases.';
COMMENT ON COLUMN course.id IS 'Stable identifier for this course record.';
COMMENT ON COLUMN course.organization_ref IS 'Owning auth_db.organization.id. The catalog verifies the publisher scope.';
COMMENT ON COLUMN course.slug IS 'URL-friendly name, unique within the publishing organization.';
COMMENT ON COLUMN course.status IS 'Catalog visibility and sale lifecycle. Archive removes the course from new sales.';
GRANT SELECT, INSERT, UPDATE ON TABLE course TO lms_course_runtime;

CREATE TABLE course_staff (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES course(id),
    user_ref uuid NOT NULL,
    duty text NOT NULL DEFAULT 'instructor' CHECK (duty IN ('instructor', 'editor', 'reviewer')),
    UNIQUE (course_id, user_ref, duty)
);
CREATE INDEX course_staff_idx_1 ON course_staff (user_ref);
COMMENT ON TABLE course_staff IS 'Assigns a user a duty for a particular course.';
COMMENT ON COLUMN course_staff.id IS 'Stable identifier for this course staff record.';
COMMENT ON COLUMN course_staff.course_id IS 'Course covered by the assignment.';
COMMENT ON COLUMN course_staff.user_ref IS 'Assigned auth_db.app_user.id. Check current membership and permissions before allowing an action.';
COMMENT ON COLUMN course_staff.duty IS 'Course-specific responsibility of the assigned person.';
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE course_staff TO lms_course_runtime;

CREATE TABLE course_release (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES course(id),
    version_no integer NOT NULL CHECK (version_no >= 1),
    published_at timestamptz,
    UNIQUE (course_id, version_no)
);
COMMENT ON TABLE course_release IS 'Version of a course containing the curriculum learners enroll against.';
COMMENT ON COLUMN course_release.id IS 'Stable identifier for this course release record.';
COMMENT ON COLUMN course_release.course_id IS 'Course whose curriculum this release represents.';
COMMENT ON COLUMN course_release.version_no IS 'Positive revision number scoped to the course.';
COMMENT ON COLUMN course_release.published_at IS 'Time this revision became immutable. NULL identifies an editable draft.';
GRANT SELECT, INSERT, UPDATE (published_at) ON TABLE course_release TO lms_course_runtime;

CREATE TABLE section (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    release_id uuid NOT NULL REFERENCES course_release(id),
    position integer NOT NULL CHECK (position >= 1),
    title text NOT NULL CHECK (btrim(title) <> ''),
    UNIQUE (release_id, position) DEFERRABLE INITIALLY IMMEDIATE
);
COMMENT ON TABLE section IS 'Ordered section within a release.';
COMMENT ON COLUMN section.id IS 'Stable identifier for this section record.';
COMMENT ON COLUMN section.release_id IS 'Course release containing this section.';
COMMENT ON COLUMN section.position IS 'One-based ordering within the release.';
COMMENT ON COLUMN section.title IS 'Section title shown in the curriculum.';
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE section TO lms_course_runtime;

CREATE TABLE lesson (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id uuid NOT NULL REFERENCES section(id),
    position integer NOT NULL CHECK (position >= 1),
    title text NOT NULL CHECK (btrim(title) <> ''),
    UNIQUE (section_id, position) DEFERRABLE INITIALLY IMMEDIATE
);
COMMENT ON TABLE lesson IS 'Ordered learning item within a section.';
COMMENT ON COLUMN lesson.id IS 'Stable identifier for this lesson record.';
COMMENT ON COLUMN lesson.section_id IS 'Section containing this lesson.';
COMMENT ON COLUMN lesson.position IS 'One-based lesson ordering within the section.';
COMMENT ON COLUMN lesson.title IS 'Lesson title displayed to the learner.';
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE lesson TO lms_course_runtime;

CREATE TABLE media_asset (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id uuid NOT NULL REFERENCES lesson(id),
    object_key text NOT NULL CHECK (btrim(object_key) <> ''),
    scan_state text NOT NULL DEFAULT 'pending' CHECK (scan_state IN ('pending', 'clean', 'blocked')),
    UNIQUE (lesson_id, object_key)
);
COMMENT ON TABLE media_asset IS 'Reference to a private object used by a lesson.';
COMMENT ON COLUMN media_asset.id IS 'Stable identifier for this media asset record.';
COMMENT ON COLUMN media_asset.lesson_id IS 'Lesson that uses this asset.';
COMMENT ON COLUMN media_asset.object_key IS 'Private object-storage key. Issue a short-lived delivery URL only after an access check.';
COMMENT ON COLUMN media_asset.scan_state IS 'Current scanner verdict. Only clean assets are eligible for delivery.';
GRANT SELECT, INSERT, UPDATE (object_key, scan_state), DELETE ON TABLE media_asset TO lms_course_runtime;

CREATE TABLE offer (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid NOT NULL REFERENCES course(id),
    currency text COLLATE "C" NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    price_minor bigint NOT NULL CHECK (price_minor >= 0),
    valid_from timestamptz NOT NULL DEFAULT now(),
    valid_until timestamptz,
    access_days integer CHECK (access_days > 0),
    CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE INDEX offer_idx_1 ON offer (course_id, valid_from);
COMMENT ON TABLE offer IS 'Price and access terms for a course during a time window.';
COMMENT ON COLUMN offer.id IS 'Stable identifier for this offer record.';
COMMENT ON COLUMN offer.course_id IS 'Course sold by this offer.';
COMMENT ON COLUMN offer.currency IS 'Three-letter currency code. The payment integration validates the supported currency and its minor-unit exponent.';
COMMENT ON COLUMN offer.price_minor IS 'Price in integer currency minor units. Zero supports a free offer.';
COMMENT ON COLUMN offer.valid_from IS 'First instant when checkout may accept this offer.';
COMMENT ON COLUMN offer.valid_until IS 'Exclusive end of the offer window. NULL means ongoing.';
COMMENT ON COLUMN offer.access_days IS 'Days of access from activation. NULL means access has no scheduled end.';
GRANT SELECT, INSERT, UPDATE ON TABLE offer TO lms_course_runtime;

-- A published tree cannot be moved or edited. Scanner verdicts can still change.
CREATE FUNCTION protect_release() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, lms AS $$
BEGIN
    IF OLD.published_at IS NOT NULL OR
       (TG_OP = 'UPDATE' AND (NEW.id, NEW.course_id, NEW.version_no)
          IS DISTINCT FROM (OLD.id, OLD.course_id, OLD.version_no)) THEN
        RAISE EXCEPTION 'Release % is published or its identity was changed', OLD.id USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER protect_release BEFORE UPDATE OR DELETE ON course_release
FOR EACH ROW EXECUTE FUNCTION protect_release();

CREATE FUNCTION protect_curriculum() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, lms AS $$
DECLARE
    old_data jsonb := to_jsonb(OLD);
    new_data jsonb := to_jsonb(NEW);
    parent_column text := TG_ARGV[0];
    parent_key uuid;
    release_key uuid;
    published timestamptz;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF new_data->'id' <> old_data->'id' OR
           new_data->parent_column <> old_data->parent_column THEN
            RAISE EXCEPTION 'Curriculum identity and parent are fixed' USING ERRCODE = '23514';
        END IF;
        IF TG_TABLE_NAME = 'media_asset' AND
           (new_data - 'scan_state') = (old_data - 'scan_state') THEN
            RETURN NEW;
        END IF;
    END IF;
    parent_key := (COALESCE(new_data, old_data)->>parent_column)::uuid;
    CASE TG_TABLE_NAME
        WHEN 'section' THEN release_key := parent_key;
        WHEN 'lesson' THEN
            SELECT s.release_id INTO STRICT release_key FROM section s WHERE s.id = parent_key;
        WHEN 'media_asset' THEN
            SELECT s.release_id INTO STRICT release_key
              FROM lesson l JOIN section s ON s.id = l.section_id WHERE l.id = parent_key;
    END CASE;
    SELECT published_at INTO STRICT published FROM course_release WHERE id = release_key FOR UPDATE;
    IF published IS NOT NULL THEN
        RAISE EXCEPTION 'Release % is published', release_key USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER protect_section BEFORE INSERT OR UPDATE OR DELETE ON section
FOR EACH ROW EXECUTE FUNCTION protect_curriculum('release_id');
CREATE TRIGGER protect_lesson BEFORE INSERT OR UPDATE OR DELETE ON lesson
FOR EACH ROW EXECUTE FUNCTION protect_curriculum('section_id');
CREATE TRIGGER protect_media BEFORE INSERT OR UPDATE OR DELETE ON media_asset
FOR EACH ROW EXECUTE FUNCTION protect_curriculum('lesson_id');

COMMENT ON SCHEMA lms IS 'lms-storefront-bootstrap/v1/course';
\endif
COMMIT;

-- Orders and settlement
\connect order_db
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT pg_catalog.pg_advisory_xact_lock(185214, 86402);
SET LOCAL ROLE lms_order_owner;
SET LOCAL search_path TO pg_catalog;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT (to_regnamespace('lms') IS NOT NULL) AS schema_exists
\gset
\if :schema_exists
DO $verify$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'lms'
        AND nspowner = current_user::regrole AND obj_description(oid,'pg_namespace') = 'lms-storefront-bootstrap/v1/order') THEN
        RAISE EXCEPTION 'Unrecognized lms schema in order_db; review before migration';
    END IF;
END
$verify$;
\echo order_db: existing baseline v1 verified by marker
\else
CREATE SCHEMA lms AUTHORIZATION lms_order_owner;
REVOKE ALL ON SCHEMA lms FROM PUBLIC;
GRANT USAGE ON SCHEMA lms TO lms_order_runtime;
SET LOCAL search_path TO lms, pg_catalog;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE purchase_order (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    buyer_ref uuid NOT NULL,
    currency text COLLATE "C" NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_minor bigint NOT NULL CHECK (total_minor >= 0),
    state text NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'paid', 'partially_refunded', 'refunded', 'cancelled'))
);
CREATE INDEX purchase_order_idx_1 ON purchase_order (buyer_ref);
COMMENT ON TABLE purchase_order IS 'One checkout and its financial total.';
COMMENT ON COLUMN purchase_order.id IS 'Stable identifier for this purchase order record.';
COMMENT ON COLUMN purchase_order.buyer_ref IS 'Purchasing auth_db.app_user.id.';
COMMENT ON COLUMN purchase_order.currency IS 'Three-letter currency code. The payment integration validates the supported currency and its minor-unit exponent.';
COMMENT ON COLUMN purchase_order.total_minor IS 'Sum of all line net amounts plus line taxes, in the order currency.';
COMMENT ON COLUMN purchase_order.state IS 'Checkout lifecycle. The service advances the state after provider verification and reconciliation.';
GRANT SELECT, INSERT, UPDATE (state) ON TABLE purchase_order TO lms_order_runtime;

CREATE TABLE order_line (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES purchase_order(id),
    offer_ref uuid NOT NULL,
    course_ref uuid NOT NULL,
    release_ref uuid NOT NULL,
    title_snapshot text NOT NULL CHECK (btrim(title_snapshot) <> ''),
    net_minor bigint NOT NULL CHECK (net_minor >= 0),
    tax_minor bigint NOT NULL CHECK (tax_minor >= 0),
    access_days integer CHECK (access_days > 0)
);
CREATE INDEX order_line_idx_1 ON order_line (order_id);
COMMENT ON TABLE order_line IS 'Immutable snapshot of an accepted course purchase.';
COMMENT ON COLUMN order_line.id IS 'Stable identifier for this order line record.';
COMMENT ON COLUMN order_line.order_id IS 'Checkout containing this purchase.';
COMMENT ON COLUMN order_line.offer_ref IS 'Accepted course_db.offer.id.';
COMMENT ON COLUMN order_line.course_ref IS 'Purchased course_db.course.id. Used when issuing a grant.';
COMMENT ON COLUMN order_line.release_ref IS 'Purchased course_db.course_release.id. Snapshot validated against the course at checkout.';
COMMENT ON COLUMN order_line.title_snapshot IS 'Course title displayed at the time of sale, retained for receipts.';
COMMENT ON COLUMN order_line.net_minor IS 'Line price after discounts and before tax.';
COMMENT ON COLUMN order_line.tax_minor IS 'Tax charged on this line, in order-currency minor units.';
COMMENT ON COLUMN order_line.access_days IS 'Days of access from activation. NULL means access has no scheduled end.';
GRANT SELECT, INSERT ON TABLE order_line TO lms_order_runtime;

CREATE TABLE payment_attempt (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES purchase_order(id),
    provider text NOT NULL CHECK (btrim(provider) <> ''),
    merchant_ref text NOT NULL CHECK (btrim(merchant_ref) <> ''),
    payment_ref text CHECK (btrim(payment_ref) <> ''),
    amount_minor bigint NOT NULL CHECK (amount_minor >= 0),
    currency text COLLATE "C" NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    state text NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'captured', 'failed', 'cancelled')),
    UNIQUE (provider, merchant_ref, payment_ref),
    CHECK (state <> 'captured' OR payment_ref IS NOT NULL)
);
CREATE INDEX payment_attempt_idx_1 ON payment_attempt (order_id);
COMMENT ON TABLE payment_attempt IS 'Provider attempt to collect funds for an order.';
COMMENT ON COLUMN payment_attempt.id IS 'Stable identifier for this payment attempt record.';
COMMENT ON COLUMN payment_attempt.order_id IS 'Order being funded.';
COMMENT ON COLUMN payment_attempt.provider IS 'Payment provider code; scopes the provider identifiers.';
COMMENT ON COLUMN payment_attempt.merchant_ref IS 'Opaque merchant-account identifier at that provider.';
COMMENT ON COLUMN payment_attempt.payment_ref IS 'Provider payment identifier. NULL before the provider assigns one.';
COMMENT ON COLUMN payment_attempt.amount_minor IS 'Amount requested or captured by this attempt.';
COMMENT ON COLUMN payment_attempt.currency IS 'Three-letter currency code. The payment integration validates the supported currency and its minor-unit exponent.';
COMMENT ON COLUMN payment_attempt.state IS 'Provider-confirmed lifecycle. A captured attempt keeps its captured state after refunds.';
GRANT SELECT, INSERT, UPDATE (payment_ref, state) ON TABLE payment_attempt TO lms_order_runtime;

CREATE TABLE refund (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_attempt_id uuid NOT NULL REFERENCES payment_attempt(id),
    refund_ref text CHECK (btrim(refund_ref) <> ''),
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    state text NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'succeeded', 'failed', 'cancelled')),
    UNIQUE (payment_attempt_id, refund_ref),
    CHECK (state <> 'succeeded' OR refund_ref IS NOT NULL)
);
CREATE INDEX refund_idx_1 ON refund (payment_attempt_id);
COMMENT ON TABLE refund IS 'Provider refund against one captured payment.';
COMMENT ON COLUMN refund.id IS 'Stable identifier for this refund record.';
COMMENT ON COLUMN refund.payment_attempt_id IS 'Captured payment against which funds are returned.';
COMMENT ON COLUMN refund.refund_ref IS 'Provider refund identifier. NULL is allowed while a request is pending.';
COMMENT ON COLUMN refund.amount_minor IS 'Positive amount reserved or returned against the captured payment.';
COMMENT ON COLUMN refund.state IS 'Pending and successful refunds consume refundable balance.';
GRANT SELECT, INSERT, UPDATE (refund_ref, state) ON TABLE refund TO lms_order_runtime;

CREATE TABLE refund_line (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id uuid NOT NULL REFERENCES refund(id),
    order_line_id uuid NOT NULL REFERENCES order_line(id),
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    UNIQUE (refund_id, order_line_id)
);
CREATE INDEX refund_line_idx_1 ON refund_line (order_line_id);
COMMENT ON TABLE refund_line IS 'Allocates part of a refund to a purchased line.';
COMMENT ON COLUMN refund_line.id IS 'Stable identifier for this refund line record.';
COMMENT ON COLUMN refund_line.refund_id IS 'Refund containing this allocation.';
COMMENT ON COLUMN refund_line.order_line_id IS 'Original line whose refundable balance is consumed.';
COMMENT ON COLUMN refund_line.amount_minor IS 'Positive share of the refund applied to this line.';
GRANT SELECT, INSERT ON TABLE refund_line TO lms_order_runtime;

CREATE TABLE webhook_receipt (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider text NOT NULL CHECK (btrim(provider) <> ''),
    merchant_ref text NOT NULL CHECK (btrim(merchant_ref) <> ''),
    event_ref text NOT NULL CHECK (btrim(event_ref) <> ''),
    state text NOT NULL DEFAULT 'received' CHECK (state IN ('received', 'processed', 'ignored', 'failed')),
    UNIQUE (provider, merchant_ref, event_ref)
);
COMMENT ON TABLE webhook_receipt IS 'Deduplicates provider notifications by merchant and event identifier.';
COMMENT ON COLUMN webhook_receipt.id IS 'Stable identifier for this webhook receipt record.';
COMMENT ON COLUMN webhook_receipt.provider IS 'Provider that sent the signed callback.';
COMMENT ON COLUMN webhook_receipt.merchant_ref IS 'Provider merchant account receiving this event.';
COMMENT ON COLUMN webhook_receipt.event_ref IS 'Opaque provider event identifier, used to detect redelivery.';
COMMENT ON COLUMN webhook_receipt.state IS 'Outcome of durable webhook processing; failed records are eligible for retry.';
GRANT SELECT, INSERT, UPDATE (state) ON TABLE webhook_receipt TO lms_order_runtime;

CREATE TABLE outbox_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_key uuid NOT NULL DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL REFERENCES purchase_order(id),
    seq bigint NOT NULL CHECK (seq > 0),
    type text NOT NULL CHECK (btrim(type) <> ''),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    UNIQUE (event_key),
    UNIQUE (order_id, seq)
);
COMMENT ON TABLE outbox_event IS 'Event written in the same transaction as the order change, for publication by a durable relay.';
COMMENT ON COLUMN outbox_event.id IS 'Stable identifier for this outbox event record.';
COMMENT ON COLUMN outbox_event.event_key IS 'Stable message ID shared with subscribers for deduplication.';
COMMENT ON COLUMN outbox_event.order_id IS 'Order whose change produced the event.';
COMMENT ON COLUMN outbox_event.seq IS 'Increasing order-local sequence assigned by the publisher to detect stale or missing events.';
COMMENT ON COLUMN outbox_event.type IS 'Versioned event type describing the order transition.';
COMMENT ON COLUMN outbox_event.payload IS 'Event data for consumers. Include necessary references and amounts and exclude credentials and card data.';
GRANT SELECT, INSERT ON TABLE outbox_event TO lms_order_runtime;

-- All money-changing transactions use SERIALIZABLE so two concurrent allocations
-- cannot both spend the same balance. Applications retry the whole transaction on 40001.
CREATE FUNCTION guard_money_write() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, lms AS $$
BEGIN
    IF current_setting('transaction_isolation') <> 'serializable' THEN
        RAISE EXCEPTION 'Use SERIALIZABLE for order and refund writes' USING ERRCODE = '25001';
    END IF;
    IF TG_OP = 'INSERT' AND TG_TABLE_NAME = 'purchase_order' THEN
        IF NEW.state <> 'pending' THEN
            RAISE EXCEPTION 'Create the order pending, then settle it with its lines' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'INSERT' AND TG_TABLE_NAME = 'order_line' THEN
        IF NOT EXISTS (SELECT 1 FROM purchase_order WHERE id = NEW.order_id AND state = 'pending') THEN
            RAISE EXCEPTION 'Only a pending order can receive a purchase line' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id <> OLD.id THEN
            RAISE EXCEPTION 'Record identity is immutable' USING ERRCODE = '23514';
        END IF;
        CASE TG_TABLE_NAME
            WHEN 'purchase_order' THEN
                IF (OLD.state = 'paid' AND NEW.state NOT IN ('paid','partially_refunded','refunded'))
                   OR (OLD.state = 'partially_refunded' AND NEW.state NOT IN ('partially_refunded','refunded'))
                   OR (OLD.state IN ('refunded','cancelled') AND NEW.state <> OLD.state) THEN
                    RAISE EXCEPTION 'A settled or closed order cannot be reopened' USING ERRCODE = '23514';
                END IF;
                IF (NEW.buyer_ref, NEW.currency, NEW.total_minor) IS DISTINCT FROM
                   (OLD.buyer_ref, OLD.currency, OLD.total_minor) THEN
                    RAISE EXCEPTION 'Checkout terms are immutable' USING ERRCODE = '23514';
                END IF;
            WHEN 'payment_attempt' THEN
                IF (NEW.order_id, NEW.provider, NEW.merchant_ref, NEW.amount_minor, NEW.currency)
                   IS DISTINCT FROM (OLD.order_id, OLD.provider, OLD.merchant_ref, OLD.amount_minor, OLD.currency)
                   OR (OLD.payment_ref IS NOT NULL AND NEW.payment_ref IS DISTINCT FROM OLD.payment_ref)
                   OR (OLD.state IN ('captured','failed','cancelled') AND NEW.state <> OLD.state) THEN
                    RAISE EXCEPTION 'Payment identity, terms or final state changed' USING ERRCODE = '23514';
                END IF;
            WHEN 'refund' THEN
                IF (NEW.payment_attempt_id, NEW.amount_minor) IS DISTINCT FROM (OLD.payment_attempt_id, OLD.amount_minor)
                   OR (OLD.refund_ref IS NOT NULL AND NEW.refund_ref IS DISTINCT FROM OLD.refund_ref)
                   OR (OLD.state IN ('succeeded','failed','cancelled') AND NEW.state <> OLD.state) THEN
                    RAISE EXCEPTION 'Refund identity, amount or final state changed' USING ERRCODE = '23514';
                END IF;
            ELSE
                RAISE EXCEPTION 'Financial line snapshots are immutable' USING ERRCODE = '23514';
        END CASE;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Financial records must be retained' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$$;

CREATE FUNCTION check_order_balance() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, lms AS $$
DECLARE
    k uuid;
    o purchase_order%ROWTYPE;
    line_count bigint;
    line_total numeric;
    captured numeric;
    refunded numeric;
BEGIN
    CASE TG_TABLE_NAME
        WHEN 'purchase_order' THEN k := NEW.id;
        WHEN 'order_line' THEN k := NEW.order_id;
        WHEN 'payment_attempt' THEN k := NEW.order_id;
        WHEN 'refund' THEN
            SELECT order_id INTO STRICT k FROM payment_attempt WHERE id = NEW.payment_attempt_id;
        WHEN 'refund_line' THEN
            SELECT order_id INTO STRICT k FROM order_line WHERE id = NEW.order_line_id;
    END CASE;
    SELECT * INTO STRICT o FROM purchase_order WHERE id = k;
    SELECT count(*), COALESCE(sum(net_minor::numeric + tax_minor), 0)
      INTO line_count, line_total FROM order_line WHERE order_id = k;
    SELECT COALESCE(sum(amount_minor) FILTER (WHERE state = 'captured'), 0)
      INTO captured FROM payment_attempt WHERE order_id = k;
    SELECT COALESCE(sum(r.amount_minor),0) INTO refunded
      FROM refund r JOIN payment_attempt p ON p.id = r.payment_attempt_id
     WHERE p.order_id = k AND r.state = 'succeeded';

    IF line_count = 0 OR line_total <> o.total_minor OR captured > o.total_minor
       OR EXISTS (SELECT 1 FROM payment_attempt WHERE order_id = k AND currency <> o.currency)
       OR (o.state IN ('paid','partially_refunded','refunded') AND captured <> o.total_minor)
       OR (o.state = 'cancelled' AND captured <> 0)
       OR (o.state = 'pending' AND captured > 0 AND captured = o.total_minor)
       OR (o.state = 'partially_refunded' AND (refunded <= 0 OR refunded >= captured))
       OR (o.state = 'refunded' AND (refunded <= 0 OR refunded <> captured))
       OR (refunded > 0 AND o.state NOT IN ('partially_refunded','refunded'))
       OR (refunded = captured AND refunded > 0 AND o.state <> 'refunded') THEN
        RAISE EXCEPTION 'Order %, lines, currency, capture or refund state disagree', k USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1 FROM refund_line a JOIN order_line l ON l.id = a.order_line_id
        JOIN refund r ON r.id = a.refund_id JOIN payment_attempt p ON p.id = r.payment_attempt_id
        WHERE (p.order_id = k OR l.order_id = k) AND p.order_id <> l.order_id
    ) OR EXISTS (
        SELECT 1 FROM refund r JOIN payment_attempt p ON p.id = r.payment_attempt_id
        WHERE p.order_id = k AND r.state IN ('pending','succeeded')
          AND (p.state <> 'captured' OR r.amount_minor <> (
              SELECT COALESCE(sum(a.amount_minor),0) FROM refund_line a WHERE a.refund_id = r.id))
    ) OR EXISTS (
        SELECT 1 FROM payment_attempt p WHERE p.order_id = k AND p.amount_minor < (
            SELECT COALESCE(sum(r.amount_minor),0) FROM refund r WHERE r.payment_attempt_id = p.id
              AND r.state IN ('pending','succeeded'))
    ) OR EXISTS (
        SELECT 1 FROM order_line l WHERE l.order_id = k AND l.net_minor::numeric + l.tax_minor < (
            SELECT COALESCE(sum(a.amount_minor),0) FROM refund_line a JOIN refund r ON r.id = a.refund_id
            WHERE a.order_line_id = l.id AND r.state IN ('pending','succeeded'))
    ) THEN
        RAISE EXCEPTION 'Refund exceeds its balance, lacks allocation or crosses orders for %', k USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END
$$;
CREATE TRIGGER guard_money_write BEFORE INSERT OR UPDATE OR DELETE ON purchase_order
FOR EACH ROW EXECUTE FUNCTION guard_money_write();
CREATE CONSTRAINT TRIGGER check_order_balance AFTER INSERT OR UPDATE ON purchase_order
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_order_balance();

CREATE TRIGGER guard_money_write BEFORE INSERT OR UPDATE OR DELETE ON order_line
FOR EACH ROW EXECUTE FUNCTION guard_money_write();
CREATE CONSTRAINT TRIGGER check_order_balance AFTER INSERT OR UPDATE ON order_line
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_order_balance();

CREATE TRIGGER guard_money_write BEFORE INSERT OR UPDATE OR DELETE ON payment_attempt
FOR EACH ROW EXECUTE FUNCTION guard_money_write();
CREATE CONSTRAINT TRIGGER check_order_balance AFTER INSERT OR UPDATE ON payment_attempt
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_order_balance();

CREATE TRIGGER guard_money_write BEFORE INSERT OR UPDATE OR DELETE ON refund
FOR EACH ROW EXECUTE FUNCTION guard_money_write();
CREATE CONSTRAINT TRIGGER check_order_balance AFTER INSERT OR UPDATE ON refund
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_order_balance();

CREATE TRIGGER guard_money_write BEFORE INSERT OR UPDATE OR DELETE ON refund_line
FOR EACH ROW EXECUTE FUNCTION guard_money_write();
CREATE CONSTRAINT TRIGGER check_order_balance AFTER INSERT OR UPDATE ON refund_line
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_order_balance();

COMMENT ON SCHEMA lms IS 'lms-storefront-bootstrap/v1/order';
\endif
COMMIT;

-- Course access and learning records
\connect learning_db
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT pg_catalog.pg_advisory_xact_lock(185214, 86402);
SET LOCAL ROLE lms_learning_owner;
SET LOCAL search_path TO pg_catalog;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT (to_regnamespace('lms') IS NOT NULL) AS schema_exists
\gset
\if :schema_exists
DO $verify$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'lms'
        AND nspowner = current_user::regrole AND obj_description(oid,'pg_namespace') = 'lms-storefront-bootstrap/v1/learning') THEN
        RAISE EXCEPTION 'Unrecognized lms schema in learning_db; review before migration';
    END IF;
END
$verify$;
\echo learning_db: existing baseline v1 verified by marker
\else
CREATE SCHEMA lms AUTHORIZATION lms_learning_owner;
REVOKE ALL ON SCHEMA lms FROM PUBLIC;
GRANT USAGE ON SCHEMA lms TO lms_learning_runtime;
SET LOCAL search_path TO lms, pg_catalog;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE access_grant (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_ref uuid NOT NULL,
    course_ref uuid NOT NULL,
    source_kind text NOT NULL CHECK (source_kind IN ('purchase','organization','manual')),
    source_ref uuid NOT NULL,
    starts_at timestamptz NOT NULL DEFAULT now(),
    ends_at timestamptz,
    revoked_at timestamptz,
    UNIQUE (source_kind, source_ref, learner_ref, course_ref),
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX access_grant_idx_1 ON access_grant (learner_ref, course_ref);
COMMENT ON TABLE access_grant IS 'An independently revocable source of access for one learner and course.';
COMMENT ON COLUMN access_grant.id IS 'Stable identifier for this access grant record.';
COMMENT ON COLUMN access_grant.learner_ref IS 'Beneficiary auth_db.app_user.id.';
COMMENT ON COLUMN access_grant.course_ref IS 'Course covered by this grant, from course_db.course.id.';
COMMENT ON COLUMN access_grant.source_kind IS 'Identifies which authority issued the grant and how to resolve source_ref.';
COMMENT ON COLUMN access_grant.source_ref IS 'For purchase grants, order_db.order_line.id. For other grants, an issuance or assignment UUID from the granting workflow.';
COMMENT ON COLUMN access_grant.starts_at IS 'Inclusive first instant when the learner may use this grant.';
COMMENT ON COLUMN access_grant.ends_at IS 'Exclusive expiry instant. NULL means no scheduled expiry.';
COMMENT ON COLUMN access_grant.revoked_at IS 'Revocation instant. NULL means the grant has not been revoked.';
GRANT SELECT, INSERT, UPDATE (revoked_at) ON TABLE access_grant TO lms_learning_runtime;

CREATE TABLE enrollment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_ref uuid NOT NULL,
    course_ref uuid NOT NULL,
    release_ref uuid NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (learner_ref, course_ref, release_ref)
);
COMMENT ON TABLE enrollment IS 'Learning history of a learner on a specific release of a course.';
COMMENT ON COLUMN enrollment.id IS 'Stable identifier for this enrollment record.';
COMMENT ON COLUMN enrollment.learner_ref IS 'Learner auth_db.app_user.id.';
COMMENT ON COLUMN enrollment.course_ref IS 'Stable course_db.course.id for this enrollment.';
COMMENT ON COLUMN enrollment.release_ref IS 'Pinned course_db.course_release.id used to interpret lessons and assessments.';
COMMENT ON COLUMN enrollment.joined_at IS 'Time the learner started this enrollment.';
GRANT SELECT, INSERT ON TABLE enrollment TO lms_learning_runtime;

CREATE TABLE lesson_progress (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id uuid NOT NULL REFERENCES enrollment(id),
    lesson_ref uuid NOT NULL,
    completed_at timestamptz,
    UNIQUE (enrollment_id, lesson_ref)
);
COMMENT ON TABLE lesson_progress IS 'Completion record for one lesson within an enrollment.';
COMMENT ON COLUMN lesson_progress.id IS 'Stable identifier for this lesson progress record.';
COMMENT ON COLUMN lesson_progress.enrollment_id IS 'Learner and release whose progress is recorded.';
COMMENT ON COLUMN lesson_progress.lesson_ref IS 'course_db.lesson.id. The learning service verifies that it belongs to the enrolled release.';
COMMENT ON COLUMN lesson_progress.completed_at IS 'Completion time. NULL represents an existing, incomplete progress record.';
GRANT SELECT, INSERT, UPDATE (completed_at) ON TABLE lesson_progress TO lms_learning_runtime;

CREATE TABLE assessment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    release_ref uuid NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    pass_score numeric(5,2) NOT NULL CHECK (pass_score BETWEEN 0 AND 100)
);
CREATE INDEX assessment_idx_1 ON assessment (release_ref);
COMMENT ON TABLE assessment IS 'Assessment definition scoped to a course release.';
COMMENT ON COLUMN assessment.id IS 'Stable identifier for this assessment record.';
COMMENT ON COLUMN assessment.release_ref IS 'course_db.course_release.id containing this assessment.';
COMMENT ON COLUMN assessment.revision IS 'Positive revision of the assessment definition. Use a new record for a changed assessment.';
COMMENT ON COLUMN assessment.pass_score IS 'Minimum passing percentage on the 0 to 100 scale.';
GRANT SELECT, INSERT ON TABLE assessment TO lms_learning_runtime;

CREATE TABLE attempt (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id uuid NOT NULL REFERENCES assessment(id),
    enrollment_id uuid NOT NULL REFERENCES enrollment(id),
    score numeric(5,2) CHECK (score BETWEEN 0 AND 100),
    submitted_at timestamptz,
    CHECK (score IS NULL OR submitted_at IS NOT NULL)
);
CREATE INDEX attempt_idx_1 ON attempt (enrollment_id);
CREATE INDEX attempt_idx_2 ON attempt (assessment_id);
COMMENT ON TABLE attempt IS 'One assessment sitting by an enrolled learner.';
COMMENT ON COLUMN attempt.id IS 'Stable identifier for this attempt record.';
COMMENT ON COLUMN attempt.assessment_id IS 'Assessment and scoring rules used for this sitting.';
COMMENT ON COLUMN attempt.enrollment_id IS 'Enrollment of the learner making the attempt.';
COMMENT ON COLUMN attempt.score IS 'Awarded percentage. NULL until graded.';
COMMENT ON COLUMN attempt.submitted_at IS 'Submission time. NULL identifies a draft attempt.';
GRANT SELECT, INSERT, UPDATE (submitted_at, score) ON TABLE attempt TO lms_learning_runtime;

CREATE TABLE certificate (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id uuid NOT NULL REFERENCES enrollment(id),
    serial text NOT NULL CHECK (btrim(serial) <> ''),
    issued_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    UNIQUE (serial),
    CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);
CREATE INDEX certificate_idx_1 ON certificate (enrollment_id);
COMMENT ON TABLE certificate IS 'Verifiable completion credential issued for an enrollment.';
COMMENT ON COLUMN certificate.id IS 'Stable identifier for this certificate record.';
COMMENT ON COLUMN certificate.enrollment_id IS 'Enrollment whose completion earned this credential.';
COMMENT ON COLUMN certificate.serial IS 'Unique public verification code, issued by the learning service.';
COMMENT ON COLUMN certificate.issued_at IS 'Time this particular credential was issued.';
COMMENT ON COLUMN certificate.revoked_at IS 'Time this credential was withdrawn. NULL means it has not been revoked.';
GRANT SELECT, INSERT, UPDATE (revoked_at) ON TABLE certificate TO lms_learning_runtime;

CREATE TABLE event_inbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer text NOT NULL CHECK (btrim(consumer) <> ''),
    event_key uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (consumer, event_key)
);
COMMENT ON TABLE event_inbox IS 'Processed message receipt. Insert in the same transaction as the consumer action.';
COMMENT ON COLUMN event_inbox.id IS 'Stable identifier for this event inbox record.';
COMMENT ON COLUMN event_inbox.consumer IS 'Stable subscriber name. Each independent subscriber can process the same event once.';
COMMENT ON COLUMN event_inbox.event_key IS 'Message UUID supplied by the publisher, such as order_db.outbox_event.event_key.';
COMMENT ON COLUMN event_inbox.processed_at IS 'Time the consumer recorded successful processing.';
GRANT SELECT, INSERT ON TABLE event_inbox TO lms_learning_runtime;

CREATE FUNCTION check_attempt_release() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, lms AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM assessment a, enrollment e
                WHERE a.id = NEW.assessment_id AND e.id = NEW.enrollment_id
                  AND a.release_ref <> e.release_ref) THEN
        RAISE EXCEPTION 'Assessment and enrollment belong to different releases' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER check_attempt_release BEFORE INSERT OR UPDATE ON attempt
FOR EACH ROW EXECUTE FUNCTION check_attempt_release();

COMMENT ON SCHEMA lms IS 'lms-storefront-bootstrap/v1/learning';
\endif
COMMIT;

-- Notifications
\connect notification_db
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT pg_catalog.pg_advisory_xact_lock(185214, 86402);
SET LOCAL ROLE lms_notification_owner;
SET LOCAL search_path TO pg_catalog;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT (to_regnamespace('lms') IS NOT NULL) AS schema_exists
\gset
\if :schema_exists
DO $verify$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'lms'
        AND nspowner = current_user::regrole AND obj_description(oid,'pg_namespace') = 'lms-storefront-bootstrap/v1/notification') THEN
        RAISE EXCEPTION 'Unrecognized lms schema in notification_db; review before migration';
    END IF;
END
$verify$;
\echo notification_db: existing baseline v1 verified by marker
\else
CREATE SCHEMA lms AUTHORIZATION lms_notification_owner;
REVOKE ALL ON SCHEMA lms FROM PUBLIC;
GRANT USAGE ON SCHEMA lms TO lms_notification_runtime;
SET LOCAL search_path TO lms, pg_catalog;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE event_inbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer text NOT NULL CHECK (btrim(consumer) <> ''),
    event_key uuid NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (consumer, event_key)
);
COMMENT ON TABLE event_inbox IS 'Processed message receipt. Insert in the same transaction as the consumer action.';
COMMENT ON COLUMN event_inbox.id IS 'Stable identifier for this event inbox record.';
COMMENT ON COLUMN event_inbox.consumer IS 'Stable subscriber name. Each independent subscriber can process the same event once.';
COMMENT ON COLUMN event_inbox.event_key IS 'Message UUID supplied by the publisher, such as order_db.outbox_event.event_key.';
COMMENT ON COLUMN event_inbox.processed_at IS 'Time the consumer recorded successful processing.';
GRANT SELECT, INSERT ON TABLE event_inbox TO lms_notification_runtime;

CREATE TABLE delivery (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_key uuid NOT NULL,
    recipient_ref uuid NOT NULL,
    channel text NOT NULL CHECK (channel IN ('email','in_app','push')),
    state text NOT NULL DEFAULT 'queued' CHECK (state IN ('queued','sending','sent','retry','failed')),
    next_try_at timestamptz,
    UNIQUE (event_key, recipient_ref, channel)
);
CREATE INDEX delivery_idx_1 ON delivery (next_try_at) WHERE state IN ('queued','retry');
COMMENT ON TABLE delivery IS 'Delivery of an event to one recipient through one channel.';
COMMENT ON COLUMN delivery.id IS 'Stable identifier for this delivery record.';
COMMENT ON COLUMN delivery.event_key IS 'Message UUID driving this notification. Use it with recipient and channel for idempotency.';
COMMENT ON COLUMN delivery.recipient_ref IS 'Destination auth_db.app_user.id. Resolve contact details through the authorized identity or profile source.';
COMMENT ON COLUMN delivery.channel IS 'Delivery route chosen for the recipient.';
COMMENT ON COLUMN delivery.state IS 'Delivery lifecycle. Workers select queued and retryable rows.';
COMMENT ON COLUMN delivery.next_try_at IS 'Earliest retry time. NULL means no delay is scheduled.';
GRANT SELECT, INSERT, UPDATE (state, next_try_at) ON TABLE delivery TO lms_notification_runtime;

COMMENT ON SCHEMA lms IS 'lms-storefront-bootstrap/v1/notification';
\endif
COMMIT;

-- Derived course search
\connect ai_db
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT pg_catalog.pg_advisory_xact_lock(185214, 86402);
SET LOCAL ROLE lms_ai_owner;
SET LOCAL search_path TO pg_catalog;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT (to_regnamespace('lms') IS NOT NULL) AS schema_exists
\gset
\if :schema_exists
DO $verify$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'lms'
        AND nspowner = current_user::regrole AND obj_description(oid,'pg_namespace') = 'lms-storefront-bootstrap/v1/ai') THEN
        RAISE EXCEPTION 'Unrecognized lms schema in ai_db; review before migration';
    END IF;
END
$verify$;
\echo ai_db: existing baseline v1 verified by marker
\else
CREATE SCHEMA lms AUTHORIZATION lms_ai_owner;
REVOKE ALL ON SCHEMA lms FROM PUBLIC;
GRANT USAGE ON SCHEMA lms TO lms_ai_runtime;
SET LOCAL search_path TO lms, pg_catalog;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE search_chunk (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_ref uuid NOT NULL,
    release_ref uuid NOT NULL,
    lesson_ref uuid NOT NULL,
    searchable_content text NOT NULL CHECK (btrim(searchable_content) <> '')
);
CREATE INDEX search_chunk_idx_1 ON search_chunk (course_ref, release_ref, lesson_ref);
CREATE INDEX search_chunk_idx_2 ON search_chunk USING gin (to_tsvector('pg_catalog.simple'::regconfig, searchable_content));
COMMENT ON TABLE search_chunk IS 'Searchable fragment derived from published course content.';
COMMENT ON COLUMN search_chunk.id IS 'Stable identifier for this search chunk record.';
COMMENT ON COLUMN search_chunk.course_ref IS 'Owning course_db.course.id, used in course authorization and filtering.';
COMMENT ON COLUMN search_chunk.release_ref IS 'Exact course_db.course_release.id from which this text was indexed.';
COMMENT ON COLUMN search_chunk.lesson_ref IS 'Source course_db.lesson.id for retrieval and reindexing.';
COMMENT ON COLUMN search_chunk.searchable_content IS 'Text of this fragment. PostgreSQL builds a full-text index on it.';
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE search_chunk TO lms_ai_runtime;

COMMENT ON SCHEMA lms IS 'lms-storefront-bootstrap/v1/ai';
\endif
COMMIT;

-- Security audit
\connect audit_db
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT pg_catalog.pg_advisory_xact_lock(185214, 86402);
SET LOCAL ROLE lms_audit_owner;
SET LOCAL search_path TO pg_catalog;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT (to_regnamespace('lms') IS NOT NULL) AS schema_exists
\gset
\if :schema_exists
DO $verify$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'lms'
        AND nspowner = current_user::regrole AND obj_description(oid,'pg_namespace') = 'lms-storefront-bootstrap/v1/audit') THEN
        RAISE EXCEPTION 'Unrecognized lms schema in audit_db; review before migration';
    END IF;
END
$verify$;
\echo audit_db: existing baseline v1 verified by marker
\else
CREATE SCHEMA lms AUTHORIZATION lms_audit_owner;
REVOKE ALL ON SCHEMA lms FROM PUBLIC;
GRANT USAGE ON SCHEMA lms TO lms_audit_runtime, lms_audit_reader;
SET LOCAL search_path TO lms, pg_catalog;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE audit_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_ref uuid,
    target_ref uuid,
    action text NOT NULL CHECK (btrim(action) <> ''),
    occurred_at timestamptz NOT NULL DEFAULT now(),
    correlation_key uuid NOT NULL
);
CREATE INDEX audit_event_idx_1 ON audit_event (correlation_key);
CREATE INDEX audit_event_idx_2 ON audit_event (target_ref, occurred_at);
CREATE INDEX audit_event_idx_3 ON audit_event (actor_ref, occurred_at);
COMMENT ON TABLE audit_event IS 'Record of a security-sensitive action on a typed target.';
COMMENT ON COLUMN audit_event.id IS 'Stable identifier for this audit event record.';
COMMENT ON COLUMN audit_event.actor_ref IS 'Human or service-principal UUID performing the action. NULL for an unattributed or system event.';
COMMENT ON COLUMN audit_event.target_ref IS 'Identifier of the affected resource. NULL for an action with no individual target.';
COMMENT ON COLUMN audit_event.action IS 'Namespaced verb identifying the affected resource type and operation, such as course.publish.';
COMMENT ON COLUMN audit_event.occurred_at IS 'Time the audited action occurred, supplied by the producer or defaulted on receipt.';
COMMENT ON COLUMN audit_event.correlation_key IS 'UUID linking audit events and requests across services.';
GRANT INSERT ON TABLE audit_event TO lms_audit_runtime;

GRANT SELECT ON TABLE audit_event TO lms_audit_reader;
COMMENT ON SCHEMA lms IS 'lms-storefront-bootstrap/v1/audit';
\endif
COMMIT;

\echo LMS baseline ready: auth_db, course_db, order_db, learning_db, notification_db, ai_db, audit_db.
