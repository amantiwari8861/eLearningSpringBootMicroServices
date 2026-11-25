-- Cross-database data check for the JPA-backed services.
-- Usage: psql -X -h localhost -p 5432 -U <user> -d postgres -f Check.sql
-- Pass credentials through PGUSER/PGPASSWORD or a local pg_service.conf entry;
-- this file deliberately contains no credentials.
--
-- Scope: auth_db, order_db and course_db are the databases that currently hold
-- JPA tables in this local cluster. The other databases named in
-- docs/lms-platform.sql (learning_db, notification_db, ai_db, audit_db) are not
-- provisioned here; add their blocks below after they are created.

\set ON_ERROR_STOP on
\pset pager off

\echo ''
\echo '==================== auth_db ===================='
\connect auth_db

\echo '-- table inventory --'
SELECT table_name
  FROM information_schema.tables
 WHERE table_schema = 'public'
 ORDER BY table_name;

\echo '-- users: count --'
SELECT count(*) AS row_count FROM public.users;
\echo '-- users: rows --'
SELECT * FROM public.users ORDER BY id;

\echo '-- roles: count --'
SELECT count(*) AS row_count FROM public.roles;
\echo '-- roles: rows --'
SELECT * FROM public.roles ORDER BY name;

\echo '-- profiles: count --'
SELECT count(*) AS row_count FROM public.profiles;
\echo '-- profiles: rows --'
SELECT * FROM public.profiles ORDER BY full_name;

\echo '-- instructors: count --'
SELECT count(*) AS row_count FROM public.instructors;
\echo '-- instructors: rows --'
SELECT * FROM public.instructors ORDER BY profile_id;

\echo '-- trainees: count --'
SELECT count(*) AS row_count FROM public.trainees;
\echo '-- trainees: rows --'
SELECT * FROM public.trainees ORDER BY profile_id;

\echo '-- user_roles: count --'
SELECT count(*) AS row_count FROM public.user_roles;
\echo '-- user_roles: rows --'
SELECT * FROM public.user_roles ORDER BY user_id, role_id;

\echo ''
\echo '==================== order_db ===================='
\connect order_db

\echo '-- table inventory --'
SELECT table_name
  FROM information_schema.tables
 WHERE table_schema = 'public'
 ORDER BY table_name;

\echo '-- orders: count --'
SELECT count(*) AS row_count FROM public.orders;
\echo '-- orders: rows --'
SELECT * FROM public.orders ORDER BY id;

\echo '-- enrollments: count --'
SELECT count(*) AS row_count FROM public.enrollments;
\echo '-- enrollments: rows --'
SELECT * FROM public.enrollments ORDER BY enrolled_at, id;

\echo ''
\echo '==================== course_db ===================='
\connect course_db

\echo '-- table inventory --'
SELECT table_name
  FROM information_schema.tables
 WHERE table_schema = 'public'
 ORDER BY table_name;

\echo '-- categories: count --'
SELECT count(*) AS row_count FROM public.categories;
\echo '-- categories: rows --'
SELECT * FROM public.categories ORDER BY name;

\echo '-- courses: count --'
SELECT count(*) AS row_count FROM public.courses;
\echo '-- courses: rows --'
SELECT * FROM public.courses ORDER BY slug;

\echo '-- course_modules: count --'
SELECT count(*) AS row_count FROM public.course_modules;
\echo '-- course_modules: rows --'
SELECT * FROM public.course_modules ORDER BY course_id, "position";

\echo '-- lessons: count --'
SELECT count(*) AS row_count FROM public.lessons;
\echo '-- lessons: rows --'
SELECT * FROM public.lessons ORDER BY module_id, "position";

\echo ''
\echo '==================== end of check ===================='
