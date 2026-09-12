-- Managed work integrations (Notion first). Channel connectors stay in connector_*.

CREATE TABLE integration_installations (
  id text PRIMARY KEY,
  organization_id text NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  catalog_id text NOT NULL,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
  config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  secret_ciphertext text,
  action_policy_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_by text NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (organization_id, catalog_id)
);

CREATE INDEX integration_installations_org
  ON integration_installations (organization_id, catalog_id);

CREATE TABLE integration_grants (
  id text PRIMARY KEY,
  installation_id text NOT NULL REFERENCES integration_installations(id) ON DELETE CASCADE,
  user_id text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  organization_id text NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'revoked')),
  account_label text,
  access_ciphertext text NOT NULL,
  refresh_ciphertext text,
  token_expires_at timestamptz,
  scopes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (installation_id, user_id)
);

CREATE INDEX integration_grants_user
  ON integration_grants (organization_id, user_id, status);

CREATE TABLE integration_oauth_states (
  id text PRIMARY KEY,
  installation_id text NOT NULL REFERENCES integration_installations(id) ON DELETE CASCADE,
  user_id text NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  organization_id text NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  code_verifier text NOT NULL,
  redirect_after text,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX integration_oauth_states_expiry
  ON integration_oauth_states (expires_at);
