-- course-service dummy seed data for course_db.
-- Schema target: columns generated from the JPA entities in this module.
-- Usage: psql -X -h localhost -p 5432 -U <user> -d course_db -f init.sql
-- Idempotent: fixed UUIDs + ON CONFLICT DO NOTHING make re-runs safe.
-- Enum strings match the CourseLevel, CourseStatus and LessonType constants.
-- The instructor_user_id is the dummy UUID used by auth-service/src/main/resources/init.sql;
-- cross-service references are intentionally not enforced by a database FK.

BEGIN;

INSERT INTO public.categories
    (id, name, slug, description, image_url, active, created_at, updated_at, version)
VALUES
    ('76d525f7-5565-5277-a400-14417ef51116',
     'Dummy Validation Category', 'dummy-validation-category', 'Dummy category',
     'https://example.invalid/cat.png', TRUE,
     '2026-11-25T12:00:00+00:00', '2026-11-25T12:00:00+00:00', 0)
ON CONFLICT (slug) DO NOTHING;

-- Resolve the category by slug so the seed works if that row already existed
-- with a different UUID.
INSERT INTO public.courses
    (id, instructor_user_id, category_id, title, slug, description, level,
     price, status, thumbnail_url, created_at, updated_at, version)
SELECT
    '73c2cce6-321c-500c-b9b1-de59ff8c8cd1',
    '0f158e06-2d29-5d67-8c27-68b42ac92364',
    c.id,
    'Dummy Validation Course', 'dummy-validation-course', 'Dummy course',
    'BEGINNER', 199.98, 'PUBLISHED', 'https://example.invalid/thumb.png',
    '2026-11-25T12:00:00+00:00', '2026-11-25T12:00:00+00:00', 0
  FROM public.categories c
 WHERE c.slug = 'dummy-validation-category'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO public.course_modules
    (id, course_id, position, title, description, created_at, updated_at, version)
SELECT
    '2182d206-d1ee-5e25-b38e-e4af984ee513',
    c.id, 1, 'Dummy Module 1', 'Dummy module',
    '2026-11-25T12:00:00+00:00', '2026-11-25T12:00:00+00:00', 0
  FROM public.courses c
 WHERE c.slug = 'dummy-validation-course'
ON CONFLICT (course_id, position) DO NOTHING;

INSERT INTO public.lessons
    (id, module_id, position, title, type, duration_in_seconds, free_preview,
     video_url, resource_url, created_at, updated_at, version)
SELECT
    'c7f9e2c9-1cdd-5d20-b091-955a336bc532',
    m.id, 1, 'Dummy Lesson 1', 'VIDEO', 600, TRUE,
    'https://example.invalid/video.mp4', NULL,
    '2026-11-25T12:00:00+00:00', '2026-11-25T12:00:00+00:00', 0
  FROM public.course_modules m
  JOIN public.courses c ON c.id = m.course_id
 WHERE c.slug = 'dummy-validation-course' AND m.position = 1
ON CONFLICT (module_id, position) DO NOTHING;

COMMIT;
