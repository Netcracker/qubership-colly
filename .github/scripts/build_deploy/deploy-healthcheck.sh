#!/usr/bin/env bash
# Post-deploy verification: workloads, ingress, liveness probe, authenticated environments API.
# Required env: INSTALL_NAMESPACE, UI_INGRESS_NAME, UI_INGRESS_HEALTH_PATH, UI_INGRESS_API_PATH,
#   UI_HEALTHCHECK_CLIENT_ID, K8S_KEYCLOAK_EXAMPLE, COLLY_UI_HEALTHCHECK_USERNAME,
#   COLLY_UI_HEALTHCHECK_PASSWORD, DEPLOY_REF, CHART_VERSION, CHART_PATH
# Optional env: COLLY_UI_HEALTHCHECK_CLIENT_SECRET, GITHUB_STEP_SUMMARY

set -euo pipefail

: "${INSTALL_NAMESPACE:?INSTALL_NAMESPACE is required}"
: "${UI_INGRESS_NAME:?UI_INGRESS_NAME is required}"
: "${UI_INGRESS_HEALTH_PATH:?UI_INGRESS_HEALTH_PATH is required}"
: "${UI_INGRESS_API_PATH:?UI_INGRESS_API_PATH is required}"
: "${UI_HEALTHCHECK_CLIENT_ID:?UI_HEALTHCHECK_CLIENT_ID is required}"
: "${K8S_KEYCLOAK_EXAMPLE:?K8S_KEYCLOAK_EXAMPLE is required}"
: "${COLLY_UI_HEALTHCHECK_USERNAME:?COLLY_UI_HEALTHCHECK_USERNAME is required}"
: "${COLLY_UI_HEALTHCHECK_PASSWORD:?COLLY_UI_HEALTHCHECK_PASSWORD is required}"
: "${DEPLOY_REF:?DEPLOY_REF is required}"
: "${CHART_VERSION:?CHART_VERSION is required}"
: "${CHART_PATH:?CHART_PATH is required}"

workloads=(envgene-inventory-service environment-operational-service ui-service)

echo "Colly deploy verification"
echo "branch/tag=${DEPLOY_REF}"
echo "chart=${CHART_PATH}:${CHART_VERSION}"
echo "namespace=${INSTALL_NAMESPACE}"
echo ""

pods_ok=true
for deploy in "${workloads[@]}"; do
  if ! kubectl get deployment "${deploy}" -n "${INSTALL_NAMESPACE}" >/dev/null 2>&1; then
    echo "FAIL: deployment ${deploy} not found in namespace ${INSTALL_NAMESPACE}"
    exit 1
  fi
  desired="$(kubectl get deployment "${deploy}" -n "${INSTALL_NAMESPACE}" \
    -o jsonpath='{.spec.replicas}')"
  ready="$(kubectl get deployment "${deploy}" -n "${INSTALL_NAMESPACE}" \
    -o jsonpath='{.status.readyReplicas}')"
  ready="${ready:-0}"
  if [ "${ready}" != "${desired}" ] || [ "${desired}" -le 0 ]; then
    kubectl rollout status "deployment/${deploy}" -n "${INSTALL_NAMESPACE}" --timeout=10m
  fi
done

for deploy in "${workloads[@]}"; do
  desired="$(kubectl get deployment "${deploy}" -n "${INSTALL_NAMESPACE}" \
    -o jsonpath='{.spec.replicas}')"
  ready="$(kubectl get deployment "${deploy}" -n "${INSTALL_NAMESPACE}" \
    -o jsonpath='{.status.readyReplicas}')"
  ready="${ready:-0}"
  if [ "${ready}" != "${desired}" ] || [ "${desired}" -le 0 ]; then
    pods_ok=false
  fi
done
if [ "${pods_ok}" != "true" ]; then
  echo "FAIL: one or more pods are not ready"
  kubectl get pods -n "${INSTALL_NAMESPACE}" \
    -l 'app.kubernetes.io/name in (envgene-inventory-service,environment-operational-service,ui-service)' \
    -o wide
  exit 1
fi

echo "Pods:"
kubectl get pods -n "${INSTALL_NAMESPACE}" \
  -l 'app.kubernetes.io/name in (envgene-inventory-service,environment-operational-service,ui-service)'
echo ""

echo "Ingress ${UI_INGRESS_NAME}:"
if ! kubectl get ingress "${UI_INGRESS_NAME}" -n "${INSTALL_NAMESPACE}" >/dev/null 2>&1; then
  echo "FAIL: ingress ${UI_INGRESS_NAME} not found in namespace ${INSTALL_NAMESPACE}"
  exit 1
fi

ingress_host=""
for attempt in $(seq 1 12); do
  ingress_host="$(kubectl get ingress "${UI_INGRESS_NAME}" -n "${INSTALL_NAMESPACE}" \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')"
  if [ -n "${ingress_host}" ]; then
    break
  fi
  echo "  address pending (${attempt}/12)"
  sleep 10
done
if [ -z "${ingress_host}" ]; then
  echo "FAIL: ingress ${UI_INGRESS_NAME} has no load balancer hostname"
  exit 1
fi

health_path="${UI_INGRESS_HEALTH_PATH#/}"
public_url="http://${ingress_host}/"
health_url="http://${ingress_host}/${health_path}"
echo "  address: ${ingress_host}"
echo ""

echo "HTTP probe:"
echo "  GET ${health_url}"
http_code=""
curl_time=""
for attempt in $(seq 1 12); do
  curl_meta="$(curl -sS -o /dev/null -w '%{http_code} %{time_total}' \
    --connect-timeout 10 --max-time 30 "${health_url}" || echo '000 0')"
  http_code="${curl_meta%% *}"
  curl_time="${curl_meta#* }"
  if [ "${http_code}" = "200" ]; then
    echo "  HTTP ${http_code} (${curl_time}s)"
    break
  fi
  echo "  HTTP ${http_code:-000} (${attempt}/12)"
  sleep 10
done
if [ "${http_code}" != "200" ]; then
  echo "FAIL: ${health_url} returned HTTP ${http_code:-000}, expected 200"
  exit 1
fi

api_path="${UI_INGRESS_API_PATH#/}"
api_url="http://${ingress_host}/${api_path}"
token_url="${K8S_KEYCLOAK_EXAMPLE%/}/protocol/openid-connect/token"
token_args=(
  -sS -X POST "${token_url}"
  --connect-timeout 10 --max-time 30
  -d "grant_type=password"
  -d "client_id=${UI_HEALTHCHECK_CLIENT_ID}"
  -d "username=${COLLY_UI_HEALTHCHECK_USERNAME}"
  -d "password=${COLLY_UI_HEALTHCHECK_PASSWORD}"
)
if [ -n "${COLLY_UI_HEALTHCHECK_CLIENT_SECRET:-}" ]; then
  token_args+=(-d "client_secret=${COLLY_UI_HEALTHCHECK_CLIENT_SECRET}")
fi

echo ""
echo "Authenticated API probe:"
echo "  POST ${token_url}"
token_response="$(curl "${token_args[@]}" || true)"
access_token="$(echo "${token_response}" | jq -r '.access_token // empty')"
if [ -z "${access_token}" ]; then
  token_error="$(echo "${token_response}" | jq -r '.error_description // .error // "unknown token error"')"
  echo "FAIL: could not obtain access token (${token_error})"
  exit 1
fi
echo "  access token obtained"

echo "  GET ${api_url}"
api_http_code=""
api_response_file="$(mktemp)"
for attempt in $(seq 1 12); do
  api_http_code="$(curl -sS -o "${api_response_file}" -w '%{http_code}' \
    --connect-timeout 10 --max-time 30 \
    -H "Authorization: Bearer ${access_token}" \
    -H "Accept: application/json" \
    "${api_url}" || echo '000')"
  if [ "${api_http_code}" = "200" ]; then
    break
  fi
  echo "  HTTP ${api_http_code:-000} (${attempt}/12)"
  sleep 10
done
if [ "${api_http_code}" != "200" ]; then
  echo "FAIL: ${api_url} returned HTTP ${api_http_code:-000}, expected 200"
  if [ -s "${api_response_file}" ]; then
    head -c 500 "${api_response_file}" || true
    echo ""
  fi
  rm -f "${api_response_file}"
  exit 1
fi
if jq -e '. == null' "${api_response_file}" >/dev/null 2>&1; then
  echo "FAIL: ${api_url} returned null"
  rm -f "${api_response_file}"
  exit 1
fi
if ! jq -e 'type == "array"' "${api_response_file}" >/dev/null 2>&1; then
  echo "FAIL: ${api_url} did not return a JSON array"
  head -c 500 "${api_response_file}" || true
  echo ""
  rm -f "${api_response_file}"
  exit 1
fi
environment_count="$(jq 'length' "${api_response_file}")"
if [ "${environment_count}" -eq 0 ]; then
  echo "FAIL: ${api_url} returned an empty environments list (count=0)"
  rm -f "${api_response_file}"
  exit 1
fi
echo "  HTTP ${api_http_code} (environments: ${environment_count})"
echo "  environments:"
{
  printf '%s\t%s\t%s\n' "NAME" "ID" "STATUS"
  jq -r '.[] | [.name, .id, .status] | @tsv' "${api_response_file}"
} | column -t -s $'\t' | sed 's/^/  /'
environment_summary_table="$(jq -r '
  ["| Name | ID | Status |", "| --- | --- | --- |"]
  + [.[] | "| \(.name) | \(.id) | \(.status) |"]
  | .[]
' "${api_response_file}")"
rm -f "${api_response_file}"

not_ready="$(kubectl get pods -n "${INSTALL_NAMESPACE}" -o json \
  | jq '[.items[] | select(.status.phase != "Running" and .status.phase != "Succeeded")] | length')"
if [ "${not_ready}" -gt 0 ]; then
  echo "WARNING: ${not_ready} pod(s) not Running/Succeeded"
  kubectl get pods -n "${INSTALL_NAMESPACE}" -o wide
fi

echo ""
echo "OK: all checks passed"
echo "UI: ${public_url}"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "Deploy verification passed"
    echo "branch/tag=${DEPLOY_REF}"
    echo "chart=${CHART_PATH}:${CHART_VERSION}"
    echo "namespace=${INSTALL_NAMESPACE}"
    echo "UI: ${public_url}"
    echo "environments API: ${api_url} (${environment_count} item(s))"
    echo ""
    echo "${environment_summary_table}"
  } >> "${GITHUB_STEP_SUMMARY}"
fi
