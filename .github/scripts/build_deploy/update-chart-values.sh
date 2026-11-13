#!/usr/bin/env bash
# Update Chart.yaml version and image tags in values.yaml for branch builds.
# Required env: IMAGE_TAG, CHART_VERSION, GITHUB_REPOSITORY_OWNER
# Optional env: CHART_FILE, VALUES_FILE, GITHUB_STEP_SUMMARY

set -euo pipefail

: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${CHART_VERSION:?CHART_VERSION is required}"
: "${GITHUB_REPOSITORY_OWNER:?GITHUB_REPOSITORY_OWNER is required}"

CHART_FILE="${CHART_FILE:-charts/qubership-colly/Chart.yaml}"
VALUES_FILE="${VALUES_FILE:-charts/qubership-colly/values.yaml}"
owner="${GITHUB_REPOSITORY_OWNER,,}"

services=(
  envgene-inventory-service
  environment-operational-service
  ui-service
)

if [ ! -f "${CHART_FILE}" ]; then
  echo "::error::Chart file not found: ${CHART_FILE}"
  exit 1
fi

if [ ! -f "${VALUES_FILE}" ]; then
  echo "::error::Values file not found: ${VALUES_FILE}"
  exit 1
fi

sed -i "s|^version:.*|version: ${CHART_VERSION}|" "${CHART_FILE}"
echo "Set ${CHART_FILE} version to ${CHART_VERSION}"

for svc in "${services[@]}"; do
  pattern="ghcr.io/[^/]*/${svc}:[a-zA-Z0-9._-]*"
  replacement="ghcr.io/${owner}/${svc}:${IMAGE_TAG}"

  if ! grep -qE "${pattern}" "${VALUES_FILE}"; then
    echo "::error::Image reference for ${svc} not found in ${VALUES_FILE}"
    exit 1
  fi

  sed -i -E "s|${pattern}|${replacement}|g" "${VALUES_FILE}"
  echo "Updated ${svc} -> ghcr.io/${owner}/${svc}:${IMAGE_TAG}"
done

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "## Chart values updated"
    echo "- Chart version: \`${CHART_VERSION}\`"
    echo "- Image tag: \`${IMAGE_TAG}\`"
    echo "- Image owner: \`${owner}\`"
    for svc in "${services[@]}"; do
      echo "- **${svc}**: \`ghcr.io/${owner}/${svc}:${IMAGE_TAG}\`"
    done
  } >> "${GITHUB_STEP_SUMMARY}"
fi
