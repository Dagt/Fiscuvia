-- Infrastructure only. No fiscal/domain tables or RLS policies (#74/#26).
CREATE SCHEMA app AUTHORIZATION CURRENT_USER;
REVOKE ALL ON SCHEMA app FROM PUBLIC;
GRANT USAGE ON SCHEMA app TO fiscuvia_runtime;
REVOKE ALL ON SCHEMA infrastructure FROM PUBLIC;
COMMENT ON SCHEMA app IS 'Fiscuvia: empty application namespace; domain migrations require review.';
