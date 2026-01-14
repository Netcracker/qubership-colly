# Qubership Colly Configuration Guide

## Helm Charts Overview

The Qubership Colly Helm chart provides a complete deployment solution for Kubernetes environments with support for OIDC authentication, PostgreSQL database integration.

## Configuration Parameters

### Global Configuration

| Parameter | Description | Default        | Required |
|-----------|-------------|----------------|----------|
| `NAMESPACE` | Kubernetes namespace for deployment | `my-namespace` | Yes |
| `CLOUD_PUBLIC_HOST` | Public host for ingress configuration | `my.host.com`  | No |

### Application Configuration (`colly`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `colly.image` | Container image | `ghcr.io/netcracker/qubership-colly:latest` | Yes |
| `colly.serviceName` | Service name | `qubership-colly` | Yes |
| `colly.instancesRepo` | Git repository for Cloud Passports | `https://github.com/my-org/cloud-passport-samples.git` | Yes |
| `colly.cronSchedule` | Synchronization schedule | `0 0/1 * * * ?` | Yes |

### Identity Provider Configuration (`colly.idp`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `colly.idp.url` | OIDC auth server URL | `http://keycloak.../realms/colly-realm` | Yes |
| `colly.idp.clientId` | OIDC client ID | `colly` | Yes |
| `colly.idp.clientSecret` | OIDC client secret | `""` | Yes |

### Database Configuration (`colly.db`)

| Parameter | Description | Default | Required |
|-----------|-------------|------|----------|
| `colly.db.host` | PostgreSQL JDBC URL | `jdbc:postgresql://postgres.../postgres` | Yes |
| `colly.db.username` | Database username | `no` | Yes |
| `colly.db.password` | Database password | `no` | Yes |


### Ingress Configuration (`colly.ingress`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `colly.ports.http` | HTTP port | `8080` | Yes |
| `colly.ingress.className` | Ingress class | `alb` | No |
| `colly.ingress.http.annotations` | Ingress annotations | See below | No |

#### Default Ingress Annotations
```yaml
colly.ingress.http.annotations:
  alb.ingress.kubernetes.io/scheme: internet-facing
  alb.ingress.kubernetes.io/target-type: ip
```

## Installation Examples

### Basic Installation
```bash
helm install qubership-colly netcracker/qubership-colly -n my-namespace
```

### Production Installation
```bash
helm install qubership-colly netcracker/qubership-colly \
  --set colly.image=ghcr.io/netcracker/qubership-colly:v1.0.0 \
  --set colly.db.host=jdbc:postgresql://prod-postgres:5432/colly \
  --set colly.db.username=colly_user \
  --set colly.db.password=secure_password \
  --set colly.idp.url=https://auth.company.com/realms/colly \
  --set colly.idp.clientSecret=prod_client_secret \
  --set colly.instancesRepo=https://token@github.com/company/cloud-passports.git \
  --set colly.cronSchedule="0 0/5 * * * ?" \
  --set CLOUD_PUBLIC_HOST=apps.company.com
```

### Custom Values File
```yaml
# values-prod.yaml
NAMESPACE: colly-prod
CLOUD_PUBLIC_HOST: apps.company.com

colly:
  image: ghcr.io/netcracker/qubership-colly:v1.0.0
  serviceName: qubership-colly
  instancesRepo: https://token@github.com/company/cloud-passports.git
  cronSchedule: "0 0/5 * * * ?"
  
  idp:
    url: https://auth.company.com/realms/colly
    clientId: colly
    clientSecret: prod_client_secret
  
  db:
    host: jdbc:postgresql://prod-postgres:5432/colly
    username: colly_user
    password: secure_password
  
  ingress:
    className: nginx
    http:
      annotations:
        nginx.ingress.kubernetes.io/ssl-redirect: "true"
        cert-manager.io/cluster-issuer: "letsencrypt-prod"
```

```bash
helm install qubership-colly netcracker/qubership-colly -f values-prod.yaml
```

## Generated Resources

The Helm chart creates the following Kubernetes resources:

1. **Deployment** - Main application deployment with environment variables
2. **Service** - ClusterIP service exposing port 8080
3. **Ingress** - Ingress configuration with dynamic hostname
4. **Secret** - Stores sensitive data (DB credentials, IDP secrets)

## Upgrade

```bash
# Upgrade with new values
helm upgrade qubership-colly netcracker/qubership-colly \
  --set colly.image=ghcr.io/netcracker/qubership-colly:v1.1.0

# Upgrade with values file
helm upgrade qubership-colly netcracker/qubership-colly -f values-prod.yaml
```

## Uninstall

```bash
helm uninstall qubership-colly
```

## Troubleshooting

### Common Issues

1. **Database Connection Issues**
    - Verify database credentials in secret
    - Check network policies and security groups
    - Ensure PostgreSQL is accessible from cluster

2. **OIDC Authentication Issues**
    - Verify client ID and secret configuration
    - Check OIDC provider URL accessibility
    - Ensure realm and client are properly configured
    - For service-to-service calls, ensure Service Accounts are enabled in Keycloak

3. **Git Repository Access Issues**
    - Verify repository URL and credentials
    - Check network egress policies
    - Ensure proper authentication tokens
    - Check `COLLY_EIS_GIT_TOKEN` is set correctly

4. **Redis Connection Issues**
    - Verify Redis is running and accessible
    - Check `QUARKUS_REDIS_HOSTS` configuration
    - In Docker Compose, ensure infra profile is running

5. **Service Communication Issues**
    - Verify all services are healthy: `docker-compose ps`
    - Check logs for authentication errors: `docker-compose logs -f [service-name]`
    - Ensure proper network connectivity between services

### Debugging

#### Kubernetes Deployment

```bash
# Check pod logs
kubectl logs deployment/qubership-colly -n <namespace>

# Check pod status
kubectl get pods -n <namespace>

# Check secrets
kubectl get secrets -n <namespace>

# Check ingress
kubectl get ingress -n <namespace>
```

#### Docker Compose Deployment

```bash
# View all service status
docker-compose ps

# View logs for specific service
docker-compose logs -f inventory-service
docker-compose logs -f operational-service
docker-compose logs -f ui-service

# View logs for infrastructure
docker-compose logs -f keycloak
docker-compose logs -f redis

# Restart a specific service
docker-compose restart inventory-service

# Check health endpoints
curl http://localhost:8081/q/health
curl http://localhost:8080/q/health
curl http://localhost:3000/colly/v2/ui-service/health
```

### Testing Configuration

```bash
# Get user token
USER_TOKEN=$(curl -s -X POST http://localhost:8180/realms/quarkus/protocol/openid-connect/token \
  -d "client_id=ui-service" \
  -d "client_secret=secret" \
  -d "grant_type=password" \
  -d "username=alice" \
  -d "password=alice" | jq -r '.access_token')

# Test authenticated endpoint
curl -H "Authorization: Bearer $USER_TOKEN" http://localhost:8081/colly/v2/inventory-service/clusters
```

## Application Configuration

### Environment Variables

#### Inventory Service

| Variable                                   | Description                                                   | Default            |
|--------------------------------------------|---------------------------------------------------------------|--------------------|
| `COLLY_EIS_PROJECT_REPO_URL`               | Git repository URL for project configurations (see [Project Configuration Guide](PROJECT_CONFIGURATION.md)) | -                  |
| `COLLY_EIS_PROJECT_REPO_FOLDER`            | Local folder for cloned project repository                    | `./project-git`    |
| `COLLY_EIS_CLOUD_PASSPORT_FOLDER`          | Local folder for cloned Cloud Passport repositories           | `./git-repo`       |
| `COLLY_EIS_GIT_TOKEN`                      | Git token for private repository access                       | -                  |
| `COLLY_EIS_CRON_SCHEDULE`                  | Synchronization schedule for inventory data                   | `0 * * * * ?`      |
| `QUARKUS_OIDC_AUTH_SERVER_URL`             | OIDC provider URL (e.g., Keycloak realm URL)                  | -                  |
| `QUARKUS_OIDC_CLIENT_ID`                   | OIDC client ID                                                | `colly-envgene-inventory-service` |
| `QUARKUS_OIDC_CREDENTIALS_SECRET`          | OIDC client secret                                            | -                  |
| `QUARKUS_REDIS_HOSTS`                      | Redis connection URL                                          | `redis://localhost:6379` |

#### Operational Service

| Variable                                                   | Description                                                                        | Default                        |
|------------------------------------------------------------|------------------------------------------------------------------------------------|--------------------------------|
| `COLLY_ENVIRONMENT_OPERATIONAL_SERVICE_CRON_SCHEDULE`      | Cluster synchronization schedule                                                   | `0 * * * * ?`                  |
| `COLLY_ENVIRONMENT_OPERATIONAL_SERVICE_MONITORING_<NAME>_NAME`  | Define custom monitoring metric name                                          | -                              |
| `COLLY_ENVIRONMENT_OPERATIONAL_SERVICE_MONITORING_<NAME>_QUERY` | Query that calculates metric for environment                                  | -                              |
| `COLLY_ENVIRONMENT_OPERATIONAL_SERVICE_CLUSTER_RESOURCE_LOADER_THREAD_POOL_SIZE` | Parallel processing threads                         | 5                              |
| `COLLY_ENVIRONMENT_OPERATIONAL_SERVICE_CONFIG_MAP_VERSIONS_NAME`            | Name of the config map in namespace with installation status | `sd-versions`                  |
| `COLLY_ENVIRONMENT_OPERATIONAL_SERVICE_CONFIG_MAP_VERSIONS_DATA_FIELD_NAME` | Data field name in config map with installed component info   | `solution-descriptors-summary` |
| `QUARKUS_REST_CLIENT_ENVGENE_INVENTORY_SERVICE_URL`        | Inventory service URL                                                              | `http://localhost:8081`        |
| `QUARKUS_OIDC_AUTH_SERVER_URL`                             | OIDC provider URL (e.g., Keycloak realm URL)                                       | -                              |
| `QUARKUS_OIDC_CLIENT_ID`                                   | OIDC client ID                                                                     | `colly-environment-operational-service` |
| `QUARKUS_OIDC_CREDENTIALS_SECRET`                          | OIDC client secret                                                                 | -                              |
| `QUARKUS_OIDC_CLIENT_SERVICE_CLIENT_GRANT_TYPE`            | Grant type for service-to-service calls                                            | `client`                       |
| `QUARKUS_REDIS_HOSTS`                                      | Redis connection URL                                                               | `redis://redis:6379`           |

#### UI Service

| Variable                                        | Description                              | Default                        |
|-------------------------------------------------|------------------------------------------|--------------------------------|
| `QUARKUS_HTTP_PORT`                             | HTTP port for UI service                 | `3000`                         |
| `QUARKUS_REST_CLIENT_INVENTORY_SERVICE_URL`     | Inventory service URL                    | `http://localhost:8081`        |
| `QUARKUS_REST_CLIENT_OPERATIONAL_SERVICE_URL`   | Operational service URL                  | `http://localhost:8080`        |
| `QUARKUS_OIDC_AUTH_SERVER_URL`                  | OIDC provider URL                        | `http://localhost:8180/realms/quarkus` |
| `QUARKUS_OIDC_CLIENT_ID`                        | OIDC client ID                           | `ui-service`                   |
| `QUARKUS_OIDC_CREDENTIALS_SECRET`               | OIDC client secret                       | -                              |


## ENV_INSTANCES_REPO
Configure clusters using Git repositories containing Cloud Passport files:

```bash
# Single repository
ENV_INSTANCES_REPO=https://github.com/your-org/cloud-passports.git

# Multiple repositories
ENV_INSTANCES_REPO=https://github.com/repo1.git,https://github.com/repo2.git

# With authentication
ENV_INSTANCES_REPO=https://username:token@github.com/private/repo.git
```

### Cloud Passport Structure
```yaml
# cluster-config.yaml
apiVersion: v1
kind: CloudPassport
metadata:
  name: production-cluster
spec:
  cloudApiHost: https://k8s-api.example.com
  token: <k8s-token>
  environments:
    - name: production-env
      description: "Production environment"
      namespaces:
        - name: prod-api
        - name: prod-web
    - name: staging-env
      description: "Staging environment"
      namespaces:
        - name: staging-api
        - name: staging-web
```

## Docker Compose Configuration

### Profiles

The Docker Compose setup uses profiles to manage different service groups:

- **infra**: Infrastructure services (PostgreSQL, Redis, Keycloak)
- **apps**: Application services (inventory-service, operational-service, ui-service)

### Usage Examples

```bash
# Start infrastructure only
docker-compose --profile infra up -d

# Start applications only (assumes infrastructure is running)
docker-compose --profile apps up --build -d

# Start everything
docker-compose --profile infra --profile apps up --build -d

# Or use the convenience script
./docker-start.sh
```

### Service Configuration

#### Infrastructure Services

- **PostgreSQL**: Port 5432, database: `postgres`, user/password: `postgres`
- **Redis**: Port 6379, Alpine image
- **Keycloak**: Port 8180, admin/admin, realm: `quarkus`

## OIDC Authentication Configuration

### Application Types

Each service uses a specific OIDC application type:

- **inventory-service**: `service` - Validates Bearer tokens from users and services
- **operational-service**: `service` - Uses client credentials grant for service-to-service calls
- **ui-service**: `hybrid` - Handles browser login and API calls

### Keycloak Client Configuration

Required settings for each client in Keycloak:

**inventory-service & ui-service**:
- Client Authentication: ON (confidential)
- Standard Flow: ON
- Direct Access Grants: ON
- Service Accounts: OFF

**operational-service**:
- Client Authentication: ON (confidential)
- Standard Flow: ON
- Direct Access Grants: ON
- **Service Accounts: ON** (required for client_credentials flow)


## Redis Configuration

Redis is used as a caching layer to improve performance:

```properties
# Production
quarkus.redis.hosts=redis://redis:6379

# Development (with Docker)
quarkus.redis.hosts=redis://localhost:6379

# Dev mode (auto-start container)
%dev.quarkus.redis.devservices.enabled=true
```

## Monitoring Integration

Qubership Colly supports custom monitoring queries that are executed against your monitoring system:

```properties
# Define custom metrics in operational-service
colly.environment-operational-service.monitoring."running-pods".name=Running Pods
colly.environment-operational-service.monitoring."running-pods".query=your_prometheus_query{namespace=~"{namespace}"}

colly.environment-operational-service.monitoring."failed-deployments".name=Failed Deployments
colly.environment-operational-service.monitoring."failed-deployments".query=your_prometheus_query2{namespace=~"{namespace}"}
```

The `{namespace}` placeholder is automatically replaced with the actual namespace names for each environment.

### Example Configuration

```properties
# application.properties for operational-service
colly.environment-operational-service.monitoring."custom-metric".name=Custom Metric Name
colly.environment-operational-service.monitoring."custom-metric".query=your_prometheus_query{namespace=~"{namespace}"}
```

---

