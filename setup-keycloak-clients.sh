#!/bin/bash

# Setup all Keycloak clients for Colly services
# Usage: ./setup-keycloak-clients.sh [keycloak-url] [realm]

KEYCLOAK_URL=${1:-"http://localhost:8180"}
REALM=${2:-"quarkus"}

# Color output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "=========================================="
echo "Setting up Keycloak clients for Colly"
echo "=========================================="
echo "Keycloak: $KEYCLOAK_URL"
echo "Realm: $REALM"
echo ""

# Get admin token
echo "Getting admin token..."
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ "$ADMIN_TOKEN" = "null" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo "❌ Failed to get admin token"
    exit 1
fi
echo -e "${GREEN}✓ Admin token obtained${NC}"
echo ""

# Function to create/update client
create_client() {
    local CLIENT_ID=$1
    local CLIENT_SECRET=$2
    local PUBLIC_CLIENT=$3
    local SERVICE_ACCOUNTS=$4
    local DESCRIPTION=$5

    echo "=== Setting up: ${CLIENT_ID} ==="
    echo "Description: ${DESCRIPTION}"

    # Check if client exists
    CLIENT_UUID=$(curl -s "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
      -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r ".[] | select(.clientId==\"${CLIENT_ID}\") | .id")

    if [ -n "$CLIENT_UUID" ] && [ "$CLIENT_UUID" != "null" ]; then
        echo -e "${YELLOW}Client exists, updating...${NC}"

        # Update existing client
        curl -s -X PUT "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}" \
          -H "Authorization: Bearer ${ADMIN_TOKEN}" \
          -H "Content-Type: application/json" \
          -d "{
            \"clientId\": \"${CLIENT_ID}\",
            \"enabled\": true,
            \"publicClient\": ${PUBLIC_CLIENT},
            \"serviceAccountsEnabled\": ${SERVICE_ACCOUNTS},
            \"standardFlowEnabled\": true,
            \"directAccessGrantsEnabled\": true,
            \"clientAuthenticatorType\": \"client-secret\",
            \"secret\": \"${CLIENT_SECRET}\",
            \"redirectUris\": [\"*\"],
            \"webOrigins\": [\"*\"],
            \"protocol\": \"openid-connect\"
          }"
    else
        echo "Creating new client..."

        # Create new client
        curl -s -X POST "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
          -H "Authorization: Bearer ${ADMIN_TOKEN}" \
          -H "Content-Type: application/json" \
          -d "{
            \"clientId\": \"${CLIENT_ID}\",
            \"enabled\": true,
            \"publicClient\": ${PUBLIC_CLIENT},
            \"serviceAccountsEnabled\": ${SERVICE_ACCOUNTS},
            \"standardFlowEnabled\": true,
            \"directAccessGrantsEnabled\": true,
            \"clientAuthenticatorType\": \"client-secret\",
            \"secret\": \"${CLIENT_SECRET}\",
            \"redirectUris\": [\"*\"],
            \"webOrigins\": [\"*\"],
            \"protocol\": \"openid-connect\"
          }"
    fi

    echo -e "${GREEN}✓ ${CLIENT_ID} configured${NC}"
    echo "  - Public Client: ${PUBLIC_CLIENT}"
    echo "  - Service Accounts: ${SERVICE_ACCOUNTS}"
    echo "  - Client Secret: ${CLIENT_SECRET}"
    echo ""
}

# Setup inventory-service client
# Used for: hybrid mode (accepts Bearer tokens from users and services)
create_client \
    "colly-envgene-inventory-service" \
    "secret" \
    "false" \
    "false" \
    "Inventory Service - hybrid mode, accepts Bearer tokens"

# Setup operational-service client
# Used for: service mode + client credentials for outgoing calls
# Note: This client needs Service Accounts enabled for scheduled tasks
create_client \
    "colly-environment-operational-service" \
    "secret" \
    "false" \
    "true" \
    "Operational Service - service mode with client credentials"

# Setup ui-service client
# Used for: hybrid mode (supports browser login + API calls)
create_client \
    "ui-service" \
    "secret" \
    "false" \
    "false" \
    "UI Service - hybrid mode, browser + API authentication"

echo "=========================================="
echo "Summary"
echo "=========================================="
echo -e "${GREEN}✓ All clients configured successfully!${NC}"
echo ""
echo "Client configurations:"
echo "  1. colly-envgene-inventory-service (inventory-service)"
echo "     - Type: Confidential"
echo "     - Mode: hybrid (application.properties)"
echo "     - Secret: secret"
echo ""
echo "  2. colly-environment-operational-service (operational-service)"
echo "     - Type: Confidential"
echo "     - Mode: service (application.properties)"
echo "     - Service Accounts: enabled"
echo "     - Secret: secret"
echo ""
echo "  3. ui-service"
echo "     - Type: Confidential"
echo "     - Mode: hybrid (application.properties)"
echo "     - Secret: secret"
echo ""
echo "Note: Update application.properties if using different secrets"
