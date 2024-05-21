## Transit
locals {
  key_name = "kidi-key"
}
resource "vault_mount" "transit" {
  path                      = "transit"
  type                      = "transit"
  description               = "Example description"
  default_lease_ttl_seconds = 3600
  max_lease_ttl_seconds     = 86400
}

resource "vault_transit_secret_backend_key" "key" {
  backend          = vault_mount.transit.path
  name             = local.key_name
  type             = "rsa-4096"
  deletion_allowed = true # On production set to false, so that you can't delete the key.
  exportable       = true
}

## Consumer
locals {
  consumer = "consumer"
}

resource "vault_policy" "consumer" {
  name = "consumer"

  policy = <<EOT
path "${vault_mount.transit.path}/keys/${vault_transit_secret_backend_key.key.name}" {
  capabilities = ["read"]
}
EOT
}

resource "vault_identity_entity" "consumer" {
  name     = local.consumer
  policies = []
  metadata = {}
}

data "vault_auth_backend" "token" {
  path = "token"
}

resource "vault_identity_entity_alias" "consumer" {
  name           = local.consumer
  mount_accessor = data.vault_auth_backend.token.accessor
  canonical_id   = vault_identity_entity.consumer.id
}

resource "vault_token_auth_backend_role" "consumer" {
  role_name              = "${local.consumer}-role"
  allowed_policies       = [vault_policy.consumer.name]
  disallowed_policies    = ["default"]
  allowed_entity_aliases = [vault_identity_entity_alias.consumer.name]
  orphan                 = true
  token_period           = "20"
  renewable              = false
  token_explicit_max_ttl = "20"
}

## Insurer
locals {
  insurers = [
    "AAA",
    "BBB",
    "CCC",
  ]
  approle_prefix = "insurer-role-"
}

resource "vault_policy" "insurer" {
  name = "insurer"

  policy = <<EOT
path "${vault_mount.transit.path}/keys/${vault_transit_secret_backend_key.key.name}" {
  capabilities = ["read"]
}
path "${vault_mount.transit.path}/export/encryption-key/${vault_transit_secret_backend_key.key.name}" {
  capabilities = ["read"]
}
EOT
}

resource "vault_auth_backend" "approle" {
  type = "approle"
}

resource "vault_approle_auth_backend_role" "insurer" {
  for_each           = toset(local.insurers)
  backend            = vault_auth_backend.approle.path
  role_name          = "${local.approle_prefix}${each.key}"
  token_policies     = [vault_policy.insurer.name]
  secret_id_num_uses = 0
  secret_id_ttl      = 30
}

## Auth for Spring Boot
resource "vault_policy" "boot" {
  name = "boot"

  policy = <<EOT
path "auth/${vault_auth_backend.approle.path}/role/+/role-id" {
  capabilities = ["read"]
}
path "auth/${vault_auth_backend.approle.path}/role/+/secret-id" {
  capabilities = ["create","update"]
}
path "auth/token/create/${vault_token_auth_backend_role.consumer.role_name}" {
  capabilities = ["create","update"]
}
path "auth/token/roles/*" {
  capabilities = ["create"]
}
EOT
}

resource "vault_approle_auth_backend_role" "boot" {
  backend            = vault_auth_backend.approle.path
  role_name          = "boot"
  token_policies     = [vault_policy.boot.name]
  secret_id_num_uses = 0
  secret_id_ttl      = 30
}

data "vault_approle_auth_backend_role_id" "boot" {
  backend   = vault_auth_backend.approle.path
  role_name = vault_approle_auth_backend_role.boot.role_name
}

resource "vault_generic_endpoint" "boot_secret_id" {
  disable_read         = true
  disable_delete       = true
  path                 = "auth/${vault_auth_backend.approle.path}/role/${vault_approle_auth_backend_role.boot.role_name}/secret-id"
  ignore_absent_fields = true
  write_fields         = ["secret_id"]

  data_json = <<EOT
{
}
EOT
}

## Template
resource "local_file" "boot_propertie" {
  content = templatefile("${path.module}/template/application.properties.tpl", {
    server_port          = "8080"
    vault_host           = var.vault_host
    vault_port           = var.vault_port
    vault_scheme         = var.vault_scheme
    app_id               = data.vault_approle_auth_backend_role_id.boot.role_id
    secret_id            = vault_generic_endpoint.boot_secret_id.write_data.secret_id
    approle_name         = vault_approle_auth_backend_role.boot.role_name
    approle_path         = vault_auth_backend.approle.path
    key_name             = local.key_name
    entity_alias_name    = vault_identity_entity_alias.consumer.name
    role_name            = vault_token_auth_backend_role.consumer.role_name
    consumer_policy_name = vault_policy.consumer.name
    insueres_codes       = join(",", local.insurers)
  })
  filename = "${path.module}/../src/main/resources/application.properties"
}