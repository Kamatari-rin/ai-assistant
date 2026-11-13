DO $$
BEGIN
    IF current_schema() IS DISTINCT FROM 'orders' THEN
        RAISE EXCEPTION 'Expected database schema: orders';
END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_namespace
        WHERE nspname = 'orders'
          AND pg_get_userbyid(nspowner) = current_user
    ) THEN
        RAISE EXCEPTION 'Database role must own schema orders';
END IF;
END
$$;
