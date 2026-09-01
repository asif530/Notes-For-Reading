#!/bin/bash
# =====================================================
# Script to connect to HashiCorp Vault and fetch secrets
# =====================================================

# --- Check dependencies ---
if ! command -v vault >/dev/null 2>&1; then
    echo "❌ vault CLI not found."
    echo "👉 Install it from: https://developer.hashicorp.com/vault/downloads"
    exit 1
fi

# --- Check arguments ---
if [ "$#" -lt 3 ]; then
    echo "Usage: $0 <VAULT_ADDR> <VAULT_TOKEN> <MOUNT_NAME>"
    echo "Example: $0 https://192.1xx.2yy.90:8200/ qwerty dev"
    exit 1
fi

# --- Assign variables ---
VAULT_ADDR="$1"
VAULT_TOKEN="$2"
VAULT_MOUNT="$3"

# --- Export environment variables ---
export VAULT_ADDR="$VAULT_ADDR"
export VAULT_TOKEN="$VAULT_TOKEN"
export VAULT_SKIP_VERIFY=true

echo "✅ Environment variables set:"
echo "   VAULT_ADDR=$VAULT_ADDR"
echo "   VAULT_SKIP_VERIFY=$VAULT_SKIP_VERIFY"
echo

# --- Run the Vault command ---
echo "🔐 Fetching secrets from Vault mount: '$VAULT_MOUNT' ..."
vault kv get -mount="$VAULT_MOUNT" "advanced-search-service"
