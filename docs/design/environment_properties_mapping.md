# Mapping of Colly attributes to EnvGene attributes

- [Mapping of Colly attributes to EnvGene attributes](#mapping-of-colly-attributes-to-envgene-attributes)
    - [Mapping table](#mapping-table)
  - [env_definition.yml example](#env_definitionyml-example)

## Mapping table

| Colly Object | Colly Attribute         | Default in Colly | Attribute Type                                                                                              | EnvGene Repo | Location in EnvGene              | Description                                         |
|--------------|-------------------------|------------------|-------------------------------------------------------------------------------------------------------------|--------------|----------------------------------|-----------------------------------------------------|
| Environment  | `name`                  |                  | string                                                                                                      | instance     | `inventory.environmentName`      | Unique name of the Environment within the Cluster   |
| Environment  | `description`           | `""`             | string                                                                                                      | instance     | `metadata.description`           | Free-form Environment description                   |
| Environment  | `owners`                | `[]`             | list of strings                                                                                             | instance     | `metadata.owners`                | User(s) responsible for the Environment             |
| Environment  | `teams`                 | `[]`             | list of strings                                                                                             | instance     | `metadata.teams`                 | Team(s) assigned to the Environment                 |
| Environment  | `labels`                | `[]`             | list of strings                                                                                             | instance     | `metadata.labels`                | Custom labels for the Environment                   |
| Environment  | `status`                | `FREE`           | enum [`IN_USE`, `RESERVED`, `FREE`, `MIGRATING`]                                                            | instance     | `metadata.status`                | Current status of the Environment                   |
| Environment  | `expirationDate`        | `""`             | LocalDate (`yyyy-MM-dd`)                                                                                    | instance     | `metadata.expirationDate`        | Date until which the Environment is allocated       |
| Environment  | `type`                  | `ENVIRONMENT`    | enum [`ENVIRONMENT`, `CSE_TOOLSET`, `DESIGN_TIME`, `APP_DEPLOYER`, `INFRASTRUCTURE`, `PORTAL`, `UNDEFINED`] | instance     | `metadata.type`                  | Technical category of the Environment               |
| Environment  | `role`                  | `""`             | string                                                                                                      | instance     | `metadata.role`                  | Usage role of the Environment (e.g. `QA`, `Dev`)    |
| Environment  | `accessGroups`          | `[]`             | list of strings                                                                                             | instance     | `metadata.accessGroups`          | Groups that have access to the Environment          |
| Environment  | `effectiveAccessGroups` | `[]`             | list of strings                                                                                             | instance     | `metadata.effectiveAccessGroups` | Computed access groups (includes inherited groups)  |
| Environment  | `sspStandalone`         | `false`          | boolean                                                                                                     | instance     | `metadata.ssp_standalone`        | Whether the Environment runs in standalone SSP mode |
| Cluster      | `description`           | `""`             | string                                                                                                      | instance     | **TBD**                          | Free-form Cluster description                       |

## env_definition.yml example

`env_definition.yml` resides in `environments/<cluster>/<environment>/Inventory/env_definition.yml` inside the instance
repository.

```yaml
metadata:
  description: "QA environment for core services"
  owners:
    - "john.doe"
    - "jane.smith"
  teams:
    - "platform-team"
  labels:
    - "qa"
    - "core"
  status: "IN_USE"                 # IN_USE | RESERVED | FREE | MIGRATING
  expirationDate: "2025-12-31"     # yyyy-MM-dd
  type: "DESIGN_TIME"              # ENVIRONMENT | CSE_TOOLSET | DESIGN_TIME | APP_DEPLOYER | INFRASTRUCTURE | PORTAL | UNDEFINED
  role: "QA"
  accessGroups:
    - "group1"
    - "group2"
  effectiveAccessGroups:
    - "group1"
    - "group2"
    - "group3"
  ssp_standalone: true

inventory:
  environmentName: "my-env"
  tenantName: "Applications"

envTemplate:
  name: "env-template-name"
  envSpecificParamsets: # DEPLOYMENT paramset overrides per deploy-postfix
    core:
      - core-deploy-ui-override
    cloud:
      - deploy-ui-override
  envSpecificTechnicalParamsets: # RUNTIME paramset overrides per deploy-postfix
    core:
      - core-runtime-ui-override
    cloud:
      - runtime-ui-override
  envSpecificE2EParamsets: # PIPELINE paramset overrides per deploy-postfix
    core:
      - core-pipeline-ui-override
    cloud:
      - pipeline-ui-override
```
