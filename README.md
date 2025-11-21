[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Netcracker_qubership-colly&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Netcracker_qubership-colly)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Netcracker_qubership-colly&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Netcracker_qubership-colly)

# Qubership Colly

A modern cluster environment tracking tool. Qubership Colly provides comprehensive visibility into Kubernetes environments across multiple clusters, helping teams track resource usage, manage environment lifecycles, and monitor common environment metrics.

## Features

- **Multi-cluster Support** - Monitor environments across multiple Kubernetes clusters
- **Cloud Passport Integration** - Native support for Cloud Passport configuration management
- **Environment Grouping** - Group multiple namespaces into logical environments
- **Environment Lifecycle Management** - Track environment creation, expiration, and cleanup
- **Rich Metadata Management** - Track owners, teams, descriptions, and custom labels for Environments
- **Deployment Version Tracking** - Monitor version information across environments
- **Monitoring Integration** - Collect and display metrics from Prometheus/monitoring systems
- **OIDC Authentication** - Secure access with Keycloak or other OIDC providers
- **Automated Discovery** - Scheduled synchronization with cluster resources
- Advanced filtering and search capabilities

## User Interface

### Environment Table
![environments.png](docs/environments.png)
The main interface displays environments in a comprehensive table with the following columns:

- **Name** - Environment name from Cloud Passport configuration
- **Namespace(s)** - Associated Kubernetes namespaces
- **Type** - Environment type (ENVIRONMENT, INFRASTRUCTURE, CSE_TOOLSET, UNDEFINED) calculated from namespace labels (editable by admins)
- **Cluster** - Source cluster name
- **Owner** - Environment owner (editable by admins)
- **Team** - Associated team (editable by admins)
- **Expiration Date** - Environment expiration date for lifecycle management
- **Status** - Current status (IN_USE, FREE, MIGRATING, RESERVED) with color coding
- **Labels** - Custom labels as chips (editable by admins)
- **Description** - Environment description from Cloud Passport or manual entry
- **Version** - Deployment version information from config maps
- **Clean Installation Date** - Last clean installation timestamp from config maps
- **Monitoring Columns** - Dynamic columns for custom monitoring metrics

### Cluster Table
![clusters.png](docs/clusters.png)

- View cluster synchronization status
- Monitor cluster connectivity
- Track cluster-specific configurations

### Administrative Features
- Edit environment metadata (type, owner, team, description, status, labels, expiration)
- Edit cluster metadata (description)
- Delete environments with confirmation

## Architecture Overview

Qubership Colly uses a microservices architecture with three main services:

```
┌─────────────┐
│   Browser   │
└──────┬──────┘
       │
       v
┌─────────────────┐
│   ui-service    │ (Port 3000)
│   - Serves UI   │
│   - BFF pattern │
│   - Aggregates  │
│     data        │
└────┬────┬───────┘
     │    │
     │    └──────────────────┐
     v                       v
┌──────────────┐    ┌────────────────────┐
│ inventory-   │    │ operational-       │
│ service      │    │ service            │
│ (Port 8081)  │    │ (Port 8080)        │
└──────────────┘    └────────────────────┘
```

### Services:
- **ui-service** (Port 3000): React-based web interface that acts as a Backend for Frontend (BFF), aggregating data from both backend services
- **inventory-service** (Port 8081): Manages environment inventory, metadata, and cluster information from Git repositories
- **operational-service** (Port 8080): Handles operational data, Kubernetes synchronization, and monitoring metrics
- **Redis**: Caching layer for improved performance
- **Keycloak**: OIDC authentication provider
- **PostgreSQL**: Database for keycloak only


Key Points:
- **Separation of Concerns**: Each service has a single, well-defined responsibility
- **BFF Pattern**: UI service aggregates data from multiple backend services
- **Scheduler**: Configurable cron jobs for cluster synchronization in operational-service
- **Authentication**: OIDC integration for secure access control


## Quick Start

### Run with Docker Compose

The easiest way to run all services together:

```bash
# Start all services (PostgreSQL, Redis, Keycloak, and all three Colly services)
./docker-start.sh

# Or manually with docker-compose
docker-compose up --build -d
```

This will start:
- **UI Service**: http://localhost:3000
- **Inventory Service**: http://localhost:8081
- **Operational Service**: http://localhost:8080
- **Keycloak**: http://localhost:8180 (admin/admin)
- PostgreSQL and Redis backends

### Build Images Manually

```bash
# Build all Docker images
./build-images.sh
```

### Deploy with Helm
```bash
# Add helm repository
helm repo add netcracker https://netcracker.github.io/helm-charts
helm repo update

# Install with basic configuration
helm install qubership-colly netcracker/qubership-colly \
  --set colly.db.password=<DB_PASSWORD> \
  --set colly.db.username=<DB_USERNAME> \
  --set colly.db.host=<DB_HOST> \
  --set colly.idp.url=http://<KEYCLOAK_HOST>:<PORT>/realms/colly-realm
```

## Configuration

For detailed configuration options including application properties, environment variables, Helm chart parameters, and deployment examples, see [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Development

### Prerequisites
- Java 21+
- Node.js 18+
- PostgreSQL 12+
- Docker and Docker Compose

### Project Structure
```
qubership-colly-v2/
├── envgene-inventory-service/   # Inventory management service
├── environment-operational-service/  # Operational data service
├── ui-service/                  # UI and BFF service
├── docker-compose.yml           # Docker Compose configuration
└── build-images.sh              # Build script for all services
```

### Build and Run Services

#### Option 1: Using Docker Compose (Recommended)
```bash
# Start all services with dependencies
./docker-start.sh
```

#### Option 2: Run Services Individually in Dev Mode

**Terminal 1 - Inventory Service:**
```bash
cd envgene-inventory-service
./mvnw quarkus:dev -Dquarkus.http.port=8081
```

**Terminal 2 - Operational Service:**
```bash
cd environment-operational-service
./mvnw quarkus:dev -Dquarkus.http.port=8080
```

**Terminal 3 - UI Service:**
```bash
cd ui-service
./mvnw quarkus:dev -Dquarkus.http.port=3000
```

**Terminal 4 - Frontend Development Server (optional):**
```bash
cd ui-service/src/main/webui
npm install
npm start  # Runs on port 3001 with proxy to backend
```

### Testing
```bash
# Test all services
cd envgene-inventory-service && mvn test
cd environment-operational-service && mvn test
cd ui-service && mvn test

# Run with coverage
mvn test jacoco:report
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

For issues and questions:
- Create an issue in the GitHub repository
- Check the [Configuration Guide](docs/CONFIGURATION.md) for detailed setup instructions
- Review troubleshooting steps in the configuration documentation
