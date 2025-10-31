# Mapping of Colly attributes to EnvGene attributes

- [Mapping of Colly attributes to EnvGene attributes](#mapping-of-colly-attributes-to-envgene-attributes)
    - [Mapping table](#mapping-table)

## Mapping table

| Colly Object | Colly Attribute  | Default in Colly | Attribute Type                                          | EnvGene Repo  | Location in EnvGene                      | Description                                       |
|--------------|------------------|------------------|---------------------------------------------------------|---------------|------------------------------------------|---------------------------------------------------|
| Environment  | `owners`         | []               | list of strings                                         | instance      | `env_definition.metadata.owners`         | User(s) responsible for the Environment           |
| Environment  | `teams`          | []               | list of strings                                         | instance      | `env_definition.metadata.teams`          | Team(s) assigned to the Environment               |
| Environment  | `status`         | `FREE`           | enum [`IN_USE`, `RESERVED`, `FREE`, `MIGRATING`]        | instance      | `env_definition.metadata.status`         | Current status of the Environment                 |
| Environment  | `expirationDate` | ""               | LocalDate ("yyyy-MM-dd")                                | instance      | `env_definition.metadata.expirationDate` | Date until which Environment is allocated         |
| Environment  | `type`           | `ENVIRONMENT`    | enum [`ENVIRONMENT`, `CSE_TOOLSET`, `DESIGN_TIME`,      | instance      | `env_definition.metadata.type`           | Defines the technical category of the Environment |
|              |                  |                  | `APP_DEPLOYER`, `INFRASTRUCTURE`, `PORTAL` `UNDEFINED`] |               |                                          |                                                   |
| Environment  | `role`           | ""               | string                                                  | instance      | `env_definition.metadata.role`           | Defines usage role of the Environment             |
| Environment  | `labels`         | []               | list of string                                          | instance      | `env_definition.metadata.labels`         | Custom labels for the Environment                 |
| Environment  | `description`    | ""               | string                                                  | instance      | `env_definition.metadata.description`    | Free-form Environment description                 |
| Cluster      | `description`    | ""               | string                                                  | instance      | **TBD**                                  | Free-form Cluster description                     |
