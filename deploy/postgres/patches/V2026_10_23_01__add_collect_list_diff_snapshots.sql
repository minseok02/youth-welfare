-- PostgreSQL 16 / psql. Default: apply in one transaction; snapshot_check_only=true: metadata checks only.
-- Runtime patch runners supply the role variables. No role creation, global grants, history edits or row changes.
\set ON_ERROR_STOP on
\if :{?snapshot_check_only}
\else
  \set snapshot_check_only false
\endif
\if :{?app_username}
\else
  \set app_username app_core_rw
\endif
\if :{?admin_ro_username}
\else
  \set admin_ro_username admin_dashboard_ro
\endif

SELECT current_database() = 'youth_welfare' AS snapshot_database_ok,
       :'snapshot_check_only' IN ('true', 'false') AS snapshot_mode_ok,
       :'app_username' <> :'admin_ro_username'
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_username')
       AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'admin_ro_username') AS snapshot_roles_ok
\gset
\if :snapshot_database_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'snapshot patch requires the reviewed youth_welfare database'; END $$;
\endif
\if :snapshot_mode_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'snapshot_check_only must be true or false'; END $$;
\endif
\if :snapshot_roles_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'existing distinct app/query roles are required'; END $$;
\endif

\if :snapshot_check_only
  BEGIN READ ONLY;
\else
  BEGIN;
\endif
SET LOCAL search_path = pg_catalog, public;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

\if :snapshot_check_only
\else
CREATE TABLE IF NOT EXISTS public.collect_list_snapshots (
    id            BIGSERIAL PRIMARY KEY,
    source_type   VARCHAR(30) NOT NULL,
    job_name      VARCHAR(50) NOT NULL,
    collected_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_count   INTEGER NOT NULL DEFAULT 0,
    new_count     INTEGER NOT NULL DEFAULT 0,
    changed_count INTEGER NOT NULL DEFAULT 0,
    missing_count INTEGER NOT NULL DEFAULT 0,
    metadata_json TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cls_source_collected
    ON public.collect_list_snapshots (source_type, collected_at DESC);

CREATE TABLE IF NOT EXISTS public.collect_list_snapshot_items (
    snapshot_id      BIGINT NOT NULL,
    source_id        VARCHAR(255) NOT NULL,
    fingerprint_hash VARCHAR(64) NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id, source_id),
    CONSTRAINT fk_clsi_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES public.collect_list_snapshots(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_clsi_source_id
    ON public.collect_list_snapshot_items (source_id);
\endif

-- IF NOT EXISTS is not a schema compatibility check. Reject incompatible required objects, never alter them here.
DO $$
DECLARE
    expected RECORD;
BEGIN
    FOR expected IN
        SELECT * FROM (VALUES
            ('collect_list_snapshots', 'id', 'int8', 'NO', NULL::integer, true),
            ('collect_list_snapshots', 'source_type', 'varchar', 'NO', 30, false),
            ('collect_list_snapshots', 'job_name', 'varchar', 'NO', 50, false),
            ('collect_list_snapshots', 'collected_at', 'timestamp', 'NO', NULL, true),
            ('collect_list_snapshots', 'total_count', 'int4', 'NO', NULL, true),
            ('collect_list_snapshots', 'new_count', 'int4', 'NO', NULL, true),
            ('collect_list_snapshots', 'changed_count', 'int4', 'NO', NULL, true),
            ('collect_list_snapshots', 'missing_count', 'int4', 'NO', NULL, true),
            ('collect_list_snapshots', 'metadata_json', 'text', 'YES', NULL, false),
            ('collect_list_snapshots', 'created_at', 'timestamp', 'NO', NULL, true),
            ('collect_list_snapshots', 'updated_at', 'timestamp', 'NO', NULL, true),
            ('collect_list_snapshot_items', 'snapshot_id', 'int8', 'NO', NULL, false),
            ('collect_list_snapshot_items', 'source_id', 'varchar', 'NO', 255, false),
            ('collect_list_snapshot_items', 'fingerprint_hash', 'varchar', 'NO', 64, false),
            ('collect_list_snapshot_items', 'created_at', 'timestamp', 'NO', NULL, true)
        ) AS required_columns(table_name, column_name, udt_name, nullable, max_length, needs_default)
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns c
            WHERE c.table_schema = 'public' AND c.table_name = expected.table_name
              AND c.column_name = expected.column_name AND c.udt_name = expected.udt_name
              AND c.is_nullable = expected.nullable
              AND c.character_maximum_length IS NOT DISTINCT FROM expected.max_length
              AND (NOT expected.needs_default OR c.column_default IS NOT NULL)
        ) THEN
            RAISE EXCEPTION 'snapshot column incompatible or invisible: %.%', expected.table_name, expected.column_name;
        END IF;
    END LOOP;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.collect_list_snapshots'::regclass
                   AND contype = 'p' AND pg_get_constraintdef(oid) = 'PRIMARY KEY (id)')
       OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.collect_list_snapshot_items'::regclass
                       AND contype = 'p' AND pg_get_constraintdef(oid) = 'PRIMARY KEY (snapshot_id, source_id)') THEN
        RAISE EXCEPTION 'snapshot primary keys are incompatible';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.collect_list_snapshot_items'::regclass
          AND confrelid = 'public.collect_list_snapshots'::regclass
          AND contype = 'f' AND confdeltype = 'c' AND convalidated
          AND conkey = ARRAY[(SELECT attnum FROM pg_attribute
                              WHERE attrelid = 'public.collect_list_snapshot_items'::regclass AND attname = 'snapshot_id')]
          AND confkey = ARRAY[(SELECT attnum FROM pg_attribute
                               WHERE attrelid = 'public.collect_list_snapshots'::regclass AND attname = 'id')]
    ) THEN
        RAISE EXCEPTION 'snapshot item foreign key is incompatible';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_index WHERE indexrelid = to_regclass('public.idx_cls_source_collected')
                   AND indrelid = 'public.collect_list_snapshots'::regclass AND indisvalid AND indisready
                   AND pg_get_indexdef(indexrelid) LIKE '%USING btree (source_type, collected_at DESC)')
       OR NOT EXISTS (SELECT 1 FROM pg_index WHERE indexrelid = to_regclass('public.idx_clsi_source_id')
                       AND indrelid = 'public.collect_list_snapshot_items'::regclass AND indisvalid AND indisready
                       AND pg_get_indexdef(indexrelid) LIKE '%USING btree (source_id)') THEN
        RAISE EXCEPTION 'snapshot lookup indexes are incompatible';
    END IF;

    IF to_regclass('public.collect_list_snapshots_id_seq') IS NULL
       OR pg_get_serial_sequence('public.collect_list_snapshots', 'id')::regclass
          IS DISTINCT FROM to_regclass('public.collect_list_snapshots_id_seq') THEN
        RAISE EXCEPTION 'snapshot id sequence ownership is incompatible';
    END IF;
END $$;

\if :snapshot_check_only
\else
GRANT SELECT, INSERT ON TABLE public.collect_list_snapshots, public.collect_list_snapshot_items TO :"app_username";
GRANT USAGE, SELECT ON SEQUENCE public.collect_list_snapshots_id_seq TO :"app_username";
GRANT SELECT ON TABLE public.collect_list_snapshots, public.collect_list_snapshot_items TO :"admin_ro_username";
\endif

-- Comma-separated privilege inquiry means ANY, not ALL: test required privileges independently.
SELECT has_schema_privilege(:'app_username', 'public', 'USAGE')
       AND has_schema_privilege(:'admin_ro_username', 'public', 'USAGE')
       AND has_table_privilege(:'app_username', 'public.collect_list_snapshots', 'SELECT')
       AND has_table_privilege(:'app_username', 'public.collect_list_snapshots', 'INSERT')
       AND has_table_privilege(:'app_username', 'public.collect_list_snapshot_items', 'SELECT')
       AND has_table_privilege(:'app_username', 'public.collect_list_snapshot_items', 'INSERT')
       AND has_sequence_privilege(:'app_username', 'public.collect_list_snapshots_id_seq', 'USAGE')
       AND has_sequence_privilege(:'app_username', 'public.collect_list_snapshots_id_seq', 'SELECT')
       AND has_table_privilege(:'admin_ro_username', 'public.collect_list_snapshots', 'SELECT')
       AND has_table_privilege(:'admin_ro_username', 'public.collect_list_snapshot_items', 'SELECT')
       AND NOT has_table_privilege(:'admin_ro_username', 'public.collect_list_snapshots', 'INSERT,UPDATE,DELETE,TRUNCATE')
       AND NOT has_table_privilege(:'admin_ro_username', 'public.collect_list_snapshot_items', 'INSERT,UPDATE,DELETE,TRUNCATE')
       AND NOT has_any_column_privilege(:'admin_ro_username', 'public.collect_list_snapshots', 'INSERT,UPDATE')
       AND NOT has_any_column_privilege(:'admin_ro_username', 'public.collect_list_snapshot_items', 'INSERT,UPDATE')
       AND NOT has_sequence_privilege(:'admin_ro_username', 'public.collect_list_snapshots_id_seq', 'USAGE,UPDATE')
       AS snapshot_privileges_ok
\gset
\if :snapshot_privileges_ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'snapshot privileges are not ready; review schema usage, grants and query-role write privileges'; END $$;
\endif

COMMIT;
\echo collect_snapshot_schema=ready
