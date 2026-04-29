# Project Git Repository Configuration Guide

This guide describes the structure of the Project Git Repository that Qubership Colly uses to manage project configurations, instance repositories, and deployment pipelines.

## Overview

The Project Git Repository stores configuration files for projects in YAML format. Each project is defined by a `parameters.yaml` (or `parameters.yml`) file located in the `projects/<project-name>/` directory.

## Repository Structure

```
project-git-repo/
├── defaults/
│   └── defaults.yaml
└── projects/
    ├── project-one/
    │   └── parameters.yaml
    ├── project-two/
    │   └── parameters.yml
    └── project-three/
        └── parameters.yaml
```

## Cluster Defaults (`defaults/defaults.yaml`)

The optional `defaults/defaults.yaml` file at the root of the project repository defines default values applied to all
clusters across all projects.

```yaml
clusters:
  owners:
    - team-ops
  roAdGroups:
    - group-readonly
  rwAdGroups:
    - group-readwrite
```

| Field        | Type          | Description                                  |
|--------------|---------------|----------------------------------------------|
| `owners`     | Array[String] | Default list of owners assigned to clusters  |
| `roAdGroups` | Array[String] | AD groups with read-only access to clusters  |
| `rwAdGroups` | Array[String] | AD groups with read-write access to clusters |

## Configuration File Schema

### Top-Level Fields

| Field             | Type          | Required | Description                                                                  |
|-------------------|---------------|----------|------------------------------------------------------------------------------|
| `customerName`    | String        | Yes      | Customer or organization name                                                |
| `name`            | String        | Yes      | Project name (unique identifier)                                             |
| `type`            | String        | Yes      | Project type: `project` or `product`                                         |
| `clusterPlatform` | String        | Yes      | Cluster platform: `k8s` or `ocp` (OpenShift Container Platform)              |
| `repositories`    | Array         | Yes      | List of repository configurations (instance repos, pipelines, template repo) |
| `accessGroups`    | Array[String] | No       | List of access group names for role-based access control                     |
| `mavenRepoName`   | String        | No       | Name of the Maven repository associated with the project                     |
| `gitGroupUrls`    | Array         | No       | List of Git group URL entries (each with `region` and `url`)                 |

### Repository Types

The `repositories` array can contain different types of repository configurations:

#### 1. EnvGene Instance Repository (`envgeneInstance`)

Repositories containing Cloud Passport configurations for environments.

| Field    | Type   | Required | Description                                                                |
|----------|--------|----------|----------------------------------------------------------------------------|
| `type`   | String | Yes      | Must be `envgeneInstance`                                                  |
| `url`    | String | Yes      | Git repository URL or local path                                           |
| `branch` | String | No       | Git branch to use (defaults to repository default branch if not specified) |
| `token`  | String | No       | Authentication token for private repositories                              |
| `region` | String | No       | Geographic region identifier (e.g., `us-east-1`, `eu-west-1`, `cn`, `mb`)  |

#### 2. Pipeline Repositories

CI/CD pipeline repositories. All pipeline types share the same set of fields.

| Field    | Type   | Required | Description                                                                |
|----------|--------|----------|----------------------------------------------------------------------------|
| `type`   | String | Yes      | Pipeline type: `clusterProvision`, `envProvision`, `solutionDeploy`, `dcl` |
| `url`    | String | Yes      | Pipeline repository URL                                                    |
| `branch` | String | No       | Git branch to use                                                          |
| `region` | String | No       | Region where pipeline operates                                             |

#### 3. EnvGene Template Repository (`envgeneTemplate`)

Template repositories for environment generation.

| Field             | Type   | Required | Description                                           |
|-------------------|--------|----------|-------------------------------------------------------|
| `type`            | String | Yes      | Must be `envgeneTemplate`                             |
| `url`             | String | Yes      | Template repository URL                               |
| `branch`          | String | No       | Git branch to use (e.g., `main`, `master`, `develop`) |
| `envgeneArtifact` | Object | Yes      | Artifact configuration (see below)                    |

**EnvGene Artifact Fields:**

| Field                           | Type   | Required | Description                                                                    |
|---------------------------------|--------|----------|--------------------------------------------------------------------------------|
| `name`                          | String | Yes      | Artifact name with optional version/tag (e.g., `my-app:feature-new-ui-123456`) |
| `defaultTemplateDescriptorName` | String | Yes      | Default template descriptor to use                                             |

## Configuration Examples

### Example 1: Product Project with Multiple Repositories

```yaml
# projects/solar_saturn/parameters.yml
customerName: Solar System
name: saturn
type: product
clusterPlatform: ocp
mavenRepoName: saturn-maven-repo
gitGroupUrls:
  - region: mb
    url: https://github.com/example/saturn-group-mb
  - region: cn
    url: https://github.com/example/saturn-group-cn
accessGroups:
  - saturn-admins
  - saturn-developers
  - platform-team
repositories:
  - type: envgeneInstance
    url: https://github.com/example/envgene-saturn
    token: saturn-envgene-token-123
    region: mb
  - type: solutionDeploy
    url: https://github.com/example/solution-deploy-saturn
  - type: clusterProvision
    url: https://github.com/example/cluster-provision-saturn
  - type: envgeneTemplate
    url: https://gitlab.com/example/template-repo.git
    branch: main
    envgeneArtifact:
      name: my-app:feature-new-ui-123456
      defaultTemplateDescriptorName: dev
```

### Example 2: Project with Multiple Regions and Feature Branch

```yaml
# projects/solar_earth/parameters.yaml
customerName: Solar System
name: earth
type: project
clusterPlatform: k8s
mavenRepoName: earth-maven-repo
gitGroupUrls:
  - region: cn
    url: https://github.com/example/earth-group-cn
  - region: eu-west-1
    url: https://github.com/example/earth-group-eu
accessGroups:
  - earth-team
repositories:
  - type: envgeneInstance
    url: https://github.com/example/envgene-earth
    branch: feature/new-environments  # Using a feature branch
    token: earth-envgene-token-789
    region: cn
  - type: clusterProvision
    url: https://github.com/example/cluster-provision-earth
    region: eu-west-1
  - type: envProvision
    url: https://github.com/example/env-provision-earth
    region: us-east-1
  - type: envgeneTemplate
    url: https://gitlab.com/example/template-repo.git
    branch: main
    envgeneArtifact:
      name: my-app:feature-new-ui-123456
      defaultTemplateDescriptorName: dev
```

### Example 3: Minimal Configuration

```yaml
# projects/minimal-project/parameters.yaml
customerName: Example Company
name: minimal
type: project
clusterPlatform: k8s
repositories:
  - type: envgeneInstance
    url: https://github.com/example/cloud-passports
```

## Field Descriptions

### Project Type

- **`project`**: Standard project configuration for development teams
- **`product`**: Product-level configuration for production deployments

### Cluster Platform

- **`k8s`**: Standard Kubernetes clusters
- **`ocp`**: Red Hat OpenShift Container Platform

### Access Groups

The `accessGroups` field enables role-based access control for projects. It defines which user groups have access to the
project.

- Groups are typically mapped to identity provider groups (e.g., LDAP, Keycloak, Active Directory)
- Group names are case-sensitive and should match exactly with the identity provider

**Example use cases:**

- Restrict project access to specific teams: `["team-backend", "team-devops"]`
- Limit access by department: `["dept-engineering"]`
- Combine role-based groups: `["project-saturn-admins", "project-saturn-developers", "project-saturn-viewers"]`

### Region

The `region` field is optional and can be used to:
- Organize environments by geographic location
- Map environments to specific cloud regions (AWS, Azure, GCP)
- Filter and group environments in the UI
- Identify deployment targets

### Maven Repository Name

The `mavenRepoName` field specifies the name of the Maven repository associated with the project. Used to resolve
artifacts during deployment.

### Git Group URL

The `gitGroupUrl` field specifies the base URL of the Git group that contains project repositories (e.g.,
`https://github.com/my-org/my-group`). Used to construct repository URLs for sub-projects within the group.

### Authentication Tokens

The `token` field is supported only for `envgeneInstance` repositories, where it is used to authenticate when cloning
the instance Git repository.

Tokens should be:
- Personal Access Tokens (PAT) for GitHub/GitLab
- Deploy tokens for read-only access
- Stored securely (consider using secrets management in production)

## Configuration in Qubership Colly

### Environment Variable

Set the project repository URL using the environment variable:

```bash
COLLY_EIS_PROJECT_REPO_URL=https://github.com/your-org/project-configs.git
```

### Multiple Projects

The inventory service scans all subdirectories under `projects/` and loads each `parameters.yaml` or `parameters.yml` file as a separate project configuration.

### Synchronization

Projects are synchronized according to the cron schedule:

```properties
# Default: every hour
colly.eis.cron.schedule=0 * * * * ?

# Development: disabled
%dev.colly.eis.cron.schedule=0 0 0 1 1 ? 2020
```

## Validation

When loading project configurations, Qubership Colly validates:

1. **Required fields**: `customerName`, `name`, `type`, `clusterPlatform` must be present
2. **Enum values**: `type` must be `project` or `product`, `clusterPlatform` must be `k8s` or `ocp`
3. **Repository types**: Each repository must have a valid `type`
4. **URL format**: Repository URLs should be valid Git URLs
5. **Template configuration**: If `envgeneTemplate` is used, `envgeneArtifact` is required

## Troubleshooting

### Project Not Loading

1. Check the file name is `parameters.yaml` or `parameters.yml`
2. Verify YAML syntax is correct
3. Ensure all required fields are present
4. Check the logs for validation errors:
   ```bash
   docker-compose logs -f inventory-service | grep -i project
   ```

### Authentication Errors

1. Verify the token has access to the repository
2. Check token permissions (read access required)
3. Ensure token is not expired
4. For private repositories, token must be provided

### Region Not Showing

1. Verify `region` field is set in the repository configuration
2. Region is optional - missing regions appear empty in the UI
3. Check environment data to ensure region is propagated

## Related Documentation

- [Configuration Guide](CONFIGURATION.md) - General configuration options
- [README](../README.md) - Project overview and quick start
