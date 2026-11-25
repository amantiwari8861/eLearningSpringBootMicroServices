-- auth-service dummy seed data for auth_db.
-- Schema target: columns generated from the JPA entities in this module.
-- Usage: psql -X -h localhost -p 5432 -U <user> -d auth_db -f init.sql
-- Idempotent: fixed UUIDs + ON CONFLICT DO NOTHING make re-runs safe.
-- Dummy values only. Do not use these credentials in a real environment.

BEGIN;

-- Role entity: id uuid, name enum, description varchar.
INSERT INTO public.roles (id, name, description) VALUES
    ('befb3504-b406-59fd-80ef-d19b96e06bdd', 'ROLE_ADMIN', 'Dummy admin role'),
    ('28020ca3-526f-5594-84eb-de5607d02dc6', 'ROLE_INSTRUCTOR', 'Dummy instructor role'),
    ('2405e851-1e8c-5ca9-8d3b-d6dcc031a49e', 'ROLE_TRAINEE', 'Dummy trainee role')
ON CONFLICT (name) DO NOTHING;

-- GenericUser entity: users(id uuid, email, password_hash).
-- There is no username, password or role column in the entity model.
INSERT INTO public.users (id, email, password_hash) VALUES
    ('0f158e06-2d29-5d67-8c27-68b42ac92364',
     'dummy.validation+instructor@example.invalid', 'DUMMY-HASH-NOT-REAL'),
    ('197b7a63-5541-5052-88db-7a1eb783cd44',
     'dummy.validation+trainee@example.invalid', 'DUMMY-HASH-NOT-REAL')
ON CONFLICT (id) DO NOTHING;

-- Profile entity: userId is a uuid FK by convention to users.id.
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

-- Instructor entity uses @MapsId: profile_id is both PK and FK to profiles.
INSERT INTO public.instructors
    (profile_id, expertise, years_of_experience, qualification, designation,
     linked_in_url, portfolio_url, status)
VALUES
    ('6cc0f0db-27aa-508b-8554-48785b790b3d', 'Spring Boot', 5, 'BSc',
     'Validation Instructor', NULL, NULL, 'PENDING')
ON CONFLICT (profile_id) DO NOTHING;

-- Trainee entity also uses @MapsId on profile_id.
INSERT INTO public.trainees
    (profile_id, qualification, institution, graduation_year, occupation, learning_goals)
VALUES
    ('3da8901b-b692-5ed0-a394-8e92f9e50228', 'BSc', 'Validation University',
     2026, 'Student', 'Learn microservices')
ON CONFLICT (profile_id) DO NOTHING;

-- GenericUser.roles join table: user_id, role_id with FKs to users and roles.
-- Role ids are resolved by name so pre-existing roles work too.
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
