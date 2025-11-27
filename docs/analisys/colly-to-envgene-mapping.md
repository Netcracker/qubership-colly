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

| Colly Object | Colly Attribute    | Attribute Type in Colly                                                                           | EnvGene Repository  | Location in EnvGene                        | Attribute Type in EnvGene | Description                                                                         |
|--------------|--------------------|---------------------------------------------------------------------------------------------------|---------------------|--------------------------------------------|---------------------------|-------------------------------------------------------------------------------------|
| Environment  | `owners`           | array of string                                                                                   | instance            | `env_definition.metadata.owners`           | array of string           | Users responsible for the Environment                                               |
| Environment  | `teams`            | array of string                                                                                   | instance            | `env_definition.metadata.teams`            | array of string           | Teams assigned to the Environment                                                   |
| Environment  | `status`           | enum [`IN_USE`, `RESERVED`, `FREE`, `MIGRATING`]                                                  | instance            | `env_definition.metadata.status`           | string                    | Current status of the Environment                                                   |
| Environment  | `expirationDate`   | string (LocalDate "yyyy-MM-dd")                                                                   | instance            | `env_definition.metadata.expirationDate`   | string                    | Date until which Environment is allocated                                           |
| Environment  | `type`             | enum [`ENVIRONMENT`, `CSE_TOOLSET`, `DESIGN_TIME`, `APP_DEPLOYER`, `INFRASTRUCTURE`, `UNDEFINED`] | instance            | `env_definition.metadata.type`             | string                    | Defines the technical category of the Environment                                   |
| Environment  | `role`             | string (the valid values is configured via a deployment parameter)                                | instance            | `env_definition.metadata.role`             | string                    | Defines usage role of the Environment                                               |
| Environment  | `labels`           | array of string                                                                                   | instance            | `env_definition.metadata.labels`           | array of string           | Custom labels for the Environment                                                   |
| Environment  | `description`      | string                                                                                            | instance            | `env_definition.metadata.description`      | string                    | Free-form Environment description                                                   |
| Environment  | `region`           | string                                                                                            | instance            | `env_definition.metadata.region`           | string                    | Geographical region associated with the Environment. This attribute is user-defined |
| Cluster      | `description`      | string                                                                                            | instance            | **TBD**                                    | string                    | Free-form Cluster description                                                       |

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

- [ ] Where to store the `description` of a Cluster

- [x] Is `ticketLinks` required?
  - The attribute is not needed by users, so it was decided to remove it

- [x] Checkov [linting errors](https://github.com/Netcracker/qubership-envgene/actions/runs/18886399036/job/53902750553)
  - Decided to ignore

- [x] Add the `deployPostfix` attribute to the Namespace?
  - No

- [x] Remove the `deploymentVersion` attribute from the Environment?
  - Yes

- [ ] Using the OpenAPI specification as documentation
  - How are descriptions and examples added to the OpenAPI spec?

- [x] What is `deploymentStatus`?
  - it is removed

- [x] Do we keep `cleanInstallationDate`? Is it computed based on SD_VERSIONS?
  - it will be removed at [MS2](https://github.com/Netcracker/qubership-colly/issues/153)

- [ ] How do we uniquely identify cluster, environment, and namespace in both services?

- [ ] How do we separate the two services?
  - Both services should return a unified schema for cluster, environment, and namespace, with all fields, but fill in only their own data. For example, for Environment:
    - inventory-service: owners, teams, status, type, role, labels, description, expirationDate
    - operational-service: cleanInstallationDate, monitoringData, deploymentStatus, lastSDDeploymentOperation, lastDeployedSDsByType
  - Two different models

## To Implement

1. Change the formation of the macros `current_env.description` and `current_env.owners` taking into account the metadata section and migration
2. Extend EnvGene `env_definition.yaml` JSON schema
