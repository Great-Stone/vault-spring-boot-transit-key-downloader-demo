terraform {
  required_providers {
    vault = {
      source  = "hashicorp/vault"
      version = "~> 4.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.0"
    }
  }
}

provider "vault" {
  address = "${var.vault_scheme}://${var.vault_host}:${var.vault_port}"
  token   = var.vault_token
}