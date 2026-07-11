# Project Git Repository Configuration Guide

This guide describes the structure of the Project Git Repository that Qubership Colly uses to manage project configurations, instance repositories, and deployment pipelines.

## Overview

The Project Git Repository stores configuration files for projects in YAML format. Each project is defined by a `parameters.yaml` (or `parameters.yml`) file located in the `projects/<project-name>/` directory.

## Repository Structure

```
project-git-repo/
└── projects/
    ├── project-one/
    │   └── parameters.yaml
    ├── project-two/
    │   └── parameters.yml
    └── project-three/
        └── parameters.yaml
```

## Configuration File Schema

### Top-Level Fields

| Field          | Type   | Required | Description                                                       |
|----------------|--------|----------|-------------------------------------------------------------------|
| `name`         | String | Yes      | Project name (unique identifier)                                  |
| `repositories` | Array  | Yes      | List of repository configurations (instance repos, template repo) |
| `gitGroupUrls` | Array  | No       | List of Git group URL entries (each with `region` and `url`)      |

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

#### 2. EnvGene Template Repository (`envgeneTemplate`)

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

### Example 1: Project with Multiple Regions

```yaml
# projects/solar_saturn/parameters.yml
name: saturn
gitGroupUrls:
  - region: mb
    url: https://github.com/example/saturn-group-mb
  - region: cn
    url: https://github.com/example/saturn-group-cn
repositories:
  - type: envgeneInstance
    url: https://github.com/example/envgene-saturn
    token: saturn-envgene-token-123
    region: mb
  - type: envgeneTemplate
    url: https://gitlab.com/example/template-repo.git
    branch: main
    envgeneArtifact:
      name: my-app:feature-new-ui-123456
      defaultTemplateDescriptorName: dev
```

### Example 2: Minimal Configuration

```yaml
# projects/minimal-project/parameters.yaml
name: minimal
repositories:
  - type: envgeneInstance
    url: https://github.com/example/cloud-passports
```

## Field Descriptions

### Region

The `region` field is optional and can be used to:
- Organize environments by geographic location
- Map environments to specific cloud regions (AWS, Azure, GCP)
- Filter and group environments in the UI
- Identify deployment targets

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

1. **Required fields**: `name` must be present
2. **Repository types**: Each repository must have a valid `type`
3. **URL format**: Repository URLs should be valid Git URLs
4. **Template configuration**: If `envgeneTemplate` is used, `envgeneArtifact` is required

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
