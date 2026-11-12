DO $$
BEGIN
    IF current_schema() IS DISTINCT FROM 'ai' THEN
        RAISE EXCEPTION 'Expected database schema: ai';
END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_namespace
        WHERE nspname = 'ai'
          AND pg_get_userbyid(nspowner) = current_user
    ) THEN
        RAISE EXCEPTION 'Database role must own schema ai';
END IF;
END
$$;
