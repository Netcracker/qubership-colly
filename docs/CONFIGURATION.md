# Qubership Colly Configuration Guide

## Overview

This guide provides comprehensive configuration options for Qubership Colly, including application properties, environment variables, and Helm chart parameters.

## Application Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ENV_INSTANCES_REPO` | Git repository URL(s) for Cloud Passport configs | - |
| `CRON_SCHEDULE` | Cluster synchronization schedule | `0 * * * * ?` |
| `COLLY_CLUSTER_RESOURCE_LOADER_THREAD_POOL_SIZE` | Parallel processing threads | 5 |

### Application Properties

```properties
# Scheduler configuration
cron.schedule=0 * * * * ?

# Cloud Passport repository
cloud.passport.folder=./git-repo

# Config map settings
colly.config-map.versions.name=sd-versions
colly.config-map.versions.data-field-name=solution-descriptors-summary

# Parallel processing
colly.cluster-resource-loader.thread-pool-size=5

# Monitoring queries
colly.monitoring."running-pods".name=Running Pods
colly.monitoring."running-pods".query=count(kube_pod_status_phase{namespace=~"{namespace}",phase="Running"})
colly.monitoring."failed-deployments".name=Failed Deployments
colly.monitoring."failed-deployments".query=count(kube_deployment_status_replicas_unavailable{namespace=~"{namespace}"})

# OIDC Authentication
quarkus.oidc.auth-server-url=<KEYCLOAK_URL>
quarkus.oidc.client-id=colly
quarkus.oidc.credentials.secret=<CLIENT_SECRET>

# Database configuration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://<HOST>:5432/<DATABASE>
quarkus.datasource.username=<USERNAME>
quarkus.datasource.password=<PASSWORD>
quarkus.hibernate-orm.database.default-schema=colly
```

## Cluster Configuration

### Cloud Passport Method (Recommended)
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

## Monitoring Integration

Qubership Colly supports custom monitoring queries that are executed against your monitoring system:

```properties
# Define custom metrics
colly.monitoring."custom-metric".name=Custom Metric Name
colly.monitoring."custom-metric".query=your_prometheus_query{namespace=~"{namespace}"}
```

The `{namespace}` placeholder is automatically replaced with the actual namespace names for each environment.

---

# Helm Chart Configuration Guide

## Overview

The Qubership Colly Helm chart provides a complete deployment solution for Kubernetes environments with support for OIDC authentication, PostgreSQL database integration, and AWS ALB ingress.

## Chart Information

- **Chart Name**: qubership-colly
- **Chart Version**: 0.0.0
- **API Version**: v2

## Configuration Parameters

### Global Configuration

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `NAMESPACE` | Kubernetes namespace for deployment | `my-namespace` | Yes |
| `CLOUD_PUBLIC_HOST` | Public host for ingress configuration | `us-east-1.elb.amazonaws.com` | No |

### Application Configuration (`colly`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `colly.image` | Container image | `ghcr.io/netcracker/qubership-colly:latest` | Yes |
| `colly.serviceName` | Service name | `qubership-colly` | Yes |
| `colly.instancesRepo` | Git repository for Cloud Passport configs | `https://github.com/ormig/cloud-passport-samples.git` | Yes |
| `colly.cronSchedule` | Synchronization schedule | `0 0/1 * * * ?` | Yes |

### Identity Provider Configuration (`colly.idp`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `colly.idp.url` | OIDC auth server URL | `http://keycloak.../realms/colly-realm` | Yes |
| `colly.idp.clientId` | OIDC client ID | `colly` | Yes |
| `colly.idp.clientSecret` | OIDC client secret | `""` | Yes |

### Database Configuration (`colly.db`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `colly.db.host` | PostgreSQL JDBC URL | `jdbc:postgresql://postgres.../postgres` | Yes |
| `colly.db.username` | Database username | `postgres` | Yes |
| `colly.db.password` | Database password | `UuFciyKFWa` | Yes |

### Network Configuration (`colly.ports`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `colly.ports.http` | HTTP port | `8080` | Yes |

### Ingress Configuration (`colly.ingress`)

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
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
helm install qubership-colly netcracker/qubership-colly \
  --set colly.db.password=mypassword \
  --set colly.idp.clientSecret=myclientsecret
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

3. **Git Repository Access Issues**
   - Verify repository URL and credentials
   - Check network egress policies
   - Ensure proper authentication tokens

### Debugging

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

## Configuration Best Practices

### Security
- Always use secrets for sensitive data (passwords, tokens)
- Use TLS for all external communications
- Implement proper RBAC for cluster access
- Regularly rotate credentials

### Performance
- Adjust thread pool size based on cluster count
- Configure appropriate cron schedules to avoid resource conflicts
- Monitor database connection pool settings
- Set proper resource limits in Kubernetes

### Monitoring
- Configure custom monitoring queries for your specific metrics
- Set up alerting for failed synchronizations
- Monitor resource usage and adjust limits accordingly
- Track application logs for troubleshooting

### High Availability
- Use external PostgreSQL with replication
- Configure multiple replicas for the application
- Implement proper health checks
- Use persistent volumes for data storage