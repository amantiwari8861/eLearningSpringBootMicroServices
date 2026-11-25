-- order-service dummy seed data for order_db.
-- Usage: psql -X -h localhost -p 5432 -U <user> -d order_db -f init.sql
-- Idempotent: fixed UUIDs + ON CONFLICT DO NOTHING make re-runs safe.
-- trainee_user_id and course_id are cross-service UUIDs seeded by the auth and
-- course init.sql files; they are intentionally not enforced by database FKs.

BEGIN;

-- The physical orders.id column is bigint with no identity, so an explicit id
-- is required. total_amount is a plain numeric in this schema (no currency column).
INSERT INTO public.orders
    (id, created_at, customer_id, product_id, quantity, status, total_amount)
VALUES
    (990001, '2026-11-25T12:00:00+00:00', 'dummy-validation-customer',
     990001, 2, 'CREATED', 199.98)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.enrollments
    (id, trainee_user_id, course_id, enrolled_at, status, version,
     completed_at, expires_at)
VALUES
    ('397c2bb2-bdd7-5334-afca-ed6ff104da20',
     '197b7a63-5541-5052-88db-7a1eb783cd44',
     '73c2cce6-321c-500c-b9b1-de59ff8c8cd1',
     '2026-11-25T12:00:00+00:00', 'ACTIVE', 0, NULL, NULL)
ON CONFLICT (trainee_user_id, course_id) DO NOTHING;

COMMIT;
