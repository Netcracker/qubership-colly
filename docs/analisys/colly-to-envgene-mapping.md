# Mapping of Colly attributes to EnvGene attributes

- [Mapping of Colly attributes to EnvGene attributes](#mapping-of-colly-attributes-to-envgene-attributes)
  - [Description](#description)
  - [Mapping table](#mapping-table)
    - [`env_definition.yml` example](#env_definitionyml-example)
  - [To Discuss](#to-discuss)
  - [To Implement](#to-implement)

## Description

This document details how Colly attributes are persisted within the EnvGene Instance repository, specifying in which files and sections each attribute is stored.

## Mapping table

| Colly Object | Colly Attribute         | Attribute Type in Colly                                                                           | EnvGene Repository | Location in EnvGene                             | Description                                                                                                 |
|--------------|-------------------------|---------------------------------------------------------------------------------------------------|--------------------|-------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Environment  | `id`                    | string                                                                                            | instance           | `env_definition.metadata.id`                    | Unique identifier of the Environment in Colly and EnvGene, matches the name                                 |
| Environment  | `name`                  | string                                                                                            | instance           | `env_definition.metadata.id`                    | Environment name; same as the identifier                                                                    |
| Environment  | `owners`                | array of string                                                                                   | instance           | `env_definition.metadata.owners`                | Users responsible for the environment (Owners in Colly)                                                     |
| Environment  | `teams`                 | array of string                                                                                   | instance           | `env_definition.metadata.teams`                 | List of teams assigned to the environment                                                                   |
| Environment  | `status`                | enum [`IN_USE`, `RESERVED`, `FREE`, `MIGRATING`, `DEPRECATED`, `PLANNED`]                         | instance           | `env_definition.metadata.status`                | Current status of the environment: in use, free, reserved, etc                                              |
| Environment  | `expirationDate`        | string (LocalDate "yyyy-MM-dd")                                                                   | instance           | `env_definition.metadata.expirationDate`        | Date until which the environment is allocated (expirationDate)                                              |
| Environment  | `type`                  | enum [`ENVIRONMENT`, `CSE_TOOLSET`, `DESIGN_TIME`, `APP_DEPLOYER`, `INFRASTRUCTURE`, `UNDEFINED`] | instance           | `env_definition.metadata.type`                  | Technical category of the environment; type of environment                                                  |
| Environment  | `role`                  | string (the list of allowed values is set via deployment parameter)                               | instance           | `env_definition.metadata.role`                  | Usage role of the environment (e.g. Dev, QA), set by parameters                                             |
| Environment  | `labels`                | array of string                                                                                   | instance           | `env_definition.metadata.labels`                | User-defined labels/tags for the environment                                                                |
| Environment  | `description`           | string                                                                                            | instance           | `env_definition.metadata.description`           | Free-form environment description                                                                           |
| Environment  | `region`                | string                                                                                            | instance           | `env_definition.metadata.region`                | Geographical region associated with the environment; set by the user                                        |
| Environment  | `accessGroups`          | array of string                                                                                   | instance           | `env_definition.metadata.accessGroups`          | List of user groups that can work with the environment                                                      |
| Environment  | `effectiveAccessGroups` | array of string                                                                                   | instance           | `env_definition.metadata.effectiveAccessGroups` | Resolved full list of user groups (contains groups and their descendants). resolved based on `accessGroups` |
| Cluster      | `name`                  | string                                                                                            | TBD                | TBD                                             | Name / Identifier of the cluster (cluster name), unique within the instance context                         |
| Cluster      | `id`                    | string                                                                                            | TBD                | TBD                                             | Unique identifier of the cluster (same as name); retained for compatibility                                 |
| Cluster      | `description`           | string                                                                                            | TBD                | TBD                                             | Free-form cluster description                                                                               |

### `env_definition.yml` example

```yaml
metadata:
  owners:
    - "user1"
    - "user2"
  teams:
    - "team-a"
    - "team-b"
  status: "IN_USE"
  expirationDate: "2024-12-31"
  type: "ENVIRONMENT"
  role: "Dev"
  labels:
    - "prod"
    - "priority-high"
  description: "very important env"
  region: cm
```

## To Discuss

- [x] Is `ticketLinks` required?
  - The attribute is not needed by users, so it was decided to remove it

- [x] Checkov [linting errors](https://github.com/Netcracker/qubership-envgene/actions/runs/18886399036/job/53902750553)
  - Decided to ignore

- [x] Add the `deployPostfix` attribute to the Namespace?
  - No

- [x] Remove the `deploymentVersion` attribute from the Environment?
  - Yes

- [x] What is `deploymentStatus`?
  - it is removed

- [x] Do we keep `cleanInstallationDate`? Is it computed based on SD_VERSIONS?
  - it will be removed at [MS2](https://github.com/Netcracker/qubership-colly/issues/153)

- [x] How do we uniquely identify cluster, environment, and namespace in both services?

- [x] How do we separate the two services?
  - Both services should return a unified schema for cluster, environment, and namespace, with all fields, but fill in only their own data. For example, for Environment:
    - inventory-service: owners, teams, status, type, role, labels, description, expirationDate
    - operational-service: cleanInstallationDate, monitoringData, deploymentStatus, lastSDDeploymentOperation, lastDeployedSDsByType
  - ~~Two different models~~

- [ ] Where to store the `description` of a Cluster

- [ ] Using the OpenAPI specification as documentation
  - How are descriptions and examples added to the OpenAPI spec?

## To Implement

- [ ] Change the formation of the macros `current_env.description` and `current_env.owners` taking into account the metadata section and migration
- [ ] Extend EnvGene `env_definition.yaml` JSON schema
