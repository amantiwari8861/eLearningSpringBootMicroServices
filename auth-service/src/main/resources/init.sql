-- auth-service dummy seed data for auth_db.
-- Usage: psql -X -h localhost -p 5432 -U <user> -d auth_db -f init.sql
-- Idempotent: fixed UUIDs + ON CONFLICT DO NOTHING make re-runs safe.
-- Dummy values only. Do not use these passwords or roles in a real environment.

BEGIN;

-- Canonical roles. Role ids are resolved by name when assigning grants below,
-- so this seed also works if the role rows were created with different UUIDs.
INSERT INTO public.roles (id, name, description) VALUES
    ('befb3504-b406-59fd-80ef-d19b96e06bdd', 'ROLE_ADMIN', 'Dummy admin role'),
    ('28020ca3-526f-5594-84eb-de5607d02dc6', 'ROLE_INSTRUCTOR', 'Dummy instructor role'),
    ('2405e851-1e8c-5ca9-8d3b-d6dcc031a49e', 'ROLE_TRAINEE', 'Dummy trainee role')
ON CONFLICT (name) DO NOTHING;

-- Local account. The physical users.id column is currently bigint with no identity,
-- so an explicit id is required. The password columns are deliberately fake.
INSERT INTO public.users (id, email, username, password, password_hash, role) VALUES
    (990001,
     'dummy.validation+instructor@example.invalid',
     'dummy_validation_instructor',
     'DUMMY-NOT-A-PASSWORD',
     'DUMMY-HASH-NOT-REAL',
     'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- Profiles use UUID user ids, matching the JPA entities rather than the current
-- bigint users.id. This mismatch is documented in the database audit; it is not
-- repaired by this seed.
INSERT INTO public.profiles
    (id, user_id, full_name, mobile_no, date_of_birth, gender,
     profile_image_url, bio, city, state, country, created_at, updated_at)
VALUES
    ('6cc0f0db-27aa-508b-8554-48785b790b3d',
     '0f158e06-2d29-5d67-8c27-68b42ac92364',
     'Dummy Validation Instructor', '+0000000000', NULL, 'PREFER_NOT_TO_SAY',
     'https://example.invalid/avatar.png', 'Dummy bio', 'Test City', 'Test State',
     'Testland', '2026-11-25T12:00:00+00:00', '2026-11-25T12:00:00+00:00'),
    ('3da8901b-b692-5ed0-a394-8e92f9e50228',
     '197b7a63-5541-5052-88db-7a1eb783cd44',
     'Dummy Validation Trainee', NULL, NULL, 'OTHER',
     NULL, 'Dummy trainee bio', 'Test City', NULL, 'Testland',
     '2026-11-25T12:00:00+00:00', '2026-11-25T12:00:00+00:00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.instructors
    (profile_id, designation, expertise, linked_in_url, portfolio_url,
     qualification, status, years_of_experience)
VALUES
    ('6cc0f0db-27aa-508b-8554-48785b790b3d', 'Validation Instructor', 'Spring Boot',
     NULL, NULL, 'BSc', 'PENDING', 5)
ON CONFLICT (profile_id) DO NOTHING;

INSERT INTO public.trainees
    (profile_id, graduation_year, institution, learning_goals, occupation, qualification)
VALUES
    ('3da8901b-b692-5ed0-a394-8e92f9e50228', 2026, 'Validation University',
     'Learn microservices', 'Student', 'BSc')
ON CONFLICT (profile_id) DO NOTHING;

-- Grants resolve role ids by name, so duplicate runs and pre-existing roles are safe.
INSERT INTO public.user_roles (user_id, role_id)
SELECT '0f158e06-2d29-5d67-8c27-68b42ac92364'::uuid, r.id
  FROM public.roles r
 WHERE r.name IN ('ROLE_ADMIN', 'ROLE_INSTRUCTOR')
UNION ALL
SELECT '197b7a63-5541-5052-88db-7a1eb783cd44'::uuid, r.id
  FROM public.roles r
 WHERE r.name = 'ROLE_TRAINEE'
ON CONFLICT DO NOTHING;

COMMIT;
