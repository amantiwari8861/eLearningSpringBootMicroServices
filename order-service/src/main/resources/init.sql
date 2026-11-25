-- order-service dummy seed data for order_db.
-- Schema target: columns generated from the JPA entity in this module.
-- Usage: psql -X -h localhost -p 5432 -U <user> -d order_db -f init.sql
-- Idempotent: fixed UUID + ON CONFLICT DO NOTHING make re-runs safe.
--
-- There is NO Order entity in the Java sources, so a JPA-created schema has no
-- `orders` table. Only `enrollments` is seeded here. trainee_user_id and
-- course_id are cross-service UUIDs from the auth and course seeds; the entity
-- deliberately has no database FK for them.

BEGIN;

-- Enrollment entity: id uuid, trainee_user_id uuid, course_id uuid, status enum,
-- enrolled_at timestamptz, completed_at, expires_at, version bigint.
INSERT INTO public.enrollments
    (id, trainee_user_id, course_id, status, enrolled_at,
     completed_at, expires_at, version)
VALUES
    ('397c2bb2-bdd7-5334-afca-ed6ff104da20',
     '197b7a63-5541-5052-88db-7a1eb783cd44',
     '73c2cce6-321c-500c-b9b1-de59ff8c8cd1',
     'ACTIVE', '2026-11-25T12:00:00+00:00', NULL, NULL, 0)
ON CONFLICT (trainee_user_id, course_id) DO NOTHING;

-- LessonProgress entity: id uuid, enrollment_id uuid, lesson_id uuid, completed,
-- completed_at timestamptz, version bigint. A unique key prevents duplicate
-- progress rows for one enrollment and lesson.
INSERT INTO public.lesson_progress
    (id, enrollment_id, lesson_id, completed, completed_at, version)
VALUES
    ('a6b4c2d8-7e91-5f30-9c2a-4d8e6b1f0a73',
     '397c2bb2-bdd7-5334-afca-ed6ff104da20',
     'c7f9e2c9-1cdd-5d20-b091-955a336bc532',
     TRUE, '2026-11-25T13:00:00+00:00', 0)
ON CONFLICT (enrollment_id, lesson_id) DO NOTHING;

COMMIT;
