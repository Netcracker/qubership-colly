# Project Repository

- [Project Repository](#project-repository)
  - [Description](#description)
  - [Repository Structure](#repository-structure)
    - [Global](#global)
      - [\[Global\] `parameters.yaml`](#global-parametersyaml)
      - [\[Global\] `credentials.yaml`](#global-credentialsyaml)
    - [Projects](#projects)
      - [\[Projects\] `parameters.yaml`](#projects-parametersyaml)
      - [\[Projects\] `credentials.yaml`](#projects-credentialsyaml)
  - [To discuss](#to-discuss)

## Description

This document describes the structure and contents of the Project repository.

## Repository Structure

```text
├── global
|   ├── parameters.yaml
|   └── credentials.yaml
└── projects
    ├── <customer-name>
    |   └── <project-name>
    |       ├── parameters.yaml
    |       └── credentials.yaml
    └── <other-project-name>
        ├── parameters.yaml
        └── credentials.yaml
```

The `<customer-name>` folder is optional.

Colly does not use `<customer-name>` or `<project-name>` from the folder names; instead, it reads `customerName` and `projectName` from inside the `parameters.yaml` file. For Colly, the projects are just a flat list. Folders are used only for better structure and readability for people.

Only two levels of folders are allowed (max depth: 2).

Any folder (within this folder depth limit) that contains a `parameters.yaml` file is considered a project folder.

### Global

#### [Global] `parameters.yaml`

```yaml
# Optional
# Global list of users with access permissions for all projects
users:
  - # Mandatory
    # User name
    name: string
    # Mandatory
    # User permissions
    permissions: enum[RO, RW]
```

#### [Global] `credentials.yaml`

Сurrently, this file has no contents

### Projects

#### [Projects] `parameters.yaml`

```yaml
# Mandatory
# Name of the customer
customerName: string
# Mandatory
# Name of the project
projectName: string
# To discuss. for different phases, there are different template versions from different branches
projectPhase: <???>
repositories:
  - # Mandatory
    # In MS1 only envgeneInstance is supported
    type: enum[ envgeneInstance, envgeneTemplate, envgeneDiscovery, pipeline ]
    # Mandatory
    url: string
    # Pointer to Credential in credentials.yaml
    # In MS1, Colly will get access to the repository using a technical user, parameters for the user will be passed as a deployment parameter
    token: creds.get('<credential-id>').secret
    # Optional
    # If not set, the "default" branch is used (as in GitLab/GitHub)
    branches: list of strings # To discuss. Do we need mapping by phase? For discovery, to get template names from different branches
    # Optional
    # Geographical region associated with the Environment. This attribute is user-defined
    # Used in cases where specific `pipeline` repositories need to be used for certain environments
    region: string
# Optional
# This is for MS1, we will do discovery later somehow
# Needs further thought because the same <artifact-template-name> can contain different templates in different versions
envgeneTemplates:
  # Mandatory
  # The key is EnvGene environment template artifact name (application from the application:version notation)
  # The value is a list of template names inside the artifact
  <artifact-template-name>: list of strings
```

#### [Projects] `credentials.yaml`

Contains [Credential](https://github.com/Netcracker/qubership-envgene/blob/main/docs/envgene-objects.md#credential) objects

```yaml
<credential-usernamePassword>:
  type: usernamePassword
  data:
    username: string
    password: string
<credential-secret>:
  type: secret
  data:
    secret: string
```

Example:

```yaml
customerName: ACME
projectName: ACME-bss
repositories:
  - type: envgeneInstance
    url: https://git.acme.com/instance
    token: instance-cred
  - region: offsite-cn
    type: pipeline
    url: https://git.acme.com/pipeline
    token: offsite-cn-pipeline-cred
  - region: offsite-mb
    type: pipeline
    url: https://git.acmemb.com/pipelines
    token: offsite-mb-pipeline-cred
  - type: envgeneTemplate
    url: https://git.acme.com/template
    token: template-cred
    branches:
      - r25.3
      - r25.4
envgeneTemplates:
  envgene-acme:
    - main
    - dt
    - dm
```

```yaml
instance-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_ba"
template-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_bb"
offsite-cn-pipeline-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_bb"
offsite-mb-pipeline-cred:
  type: secret
  data:
    secret: "MGE3MjYwNTQtZGE4My00MTlkLWIzN2MtZjU5YTg3NDA2Yzk0MzlmZmViZGUtYWY4_PF84_bb"
```

## To discuss

- [ ] Should the Project repository be used as the Maintenance inventory?

- [+] Use case for Colly using its own project repository:
  1. Read all projects and extract the URL, token, and branches from the `envgeneInstance` repositories in order to display the environments from these projects.

- [ ] Need a mapping from environment to project. For example, an environment attribute `project`.
  - Use case: find the DCL pipeline repository by environment
  - Requestor - The Customer

- [ ] global configuration

- [ ] `pipeline` is too generic, we need to specify the exact type of pipeline
